package com.niceninjapro.bedrockblockplacement.client.handler;

import com.niceninjapro.bedrockblockplacement.BedrockBlockPlacement;
import com.niceninjapro.bedrockblockplacement.client.util.BlockClippingHelper;
import com.niceninjapro.bedrockblockplacement.config.ClientConfig;
import com.niceninjapro.bedrockblockplacement.mixin.client.accessor.MinecraftAccessor;
import fuzs.puzzleslib.api.event.v1.core.EventResultHolder;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class FastPlacementHandler extends AbstractFastBlockHandler {
    public static final FastPlacementHandler INSTANCE = new FastPlacementHandler();
    /**
     * Horizontal blocks per tick the player has to move to count as moving, low enough for sneaking (about 0.065).
     */
    private static final double MIN_MOVEMENT = 0.04;
    /**
     * How much of the horizontal movement has to go in one axis direction to count as moving straight.
     */
    private static final double MIN_STRAIGHTNESS = 0.8;
    /**
     * Ticks of moving straight before fast placement starts in the walking direction (2 ticks are 100 milliseconds).
     */
    private static final int STRAIGHT_TICKS_BEFORE_START = 2;
    private static final double MILLISECONDS_PER_TICK = 50.0;
    /**
     * Cosine of the angle between looking and moving direction up to which movement counts as walking forward (0.5 is
     * 60 degrees).
     */
    private static final double MIN_FORWARD_ALIGNMENT = 0.5;

    @Nullable
    private Vec3 hitLocation;
    @Nullable
    private InteractionHand interactionHand;
    @Nullable
    private Direction candidateDirection;
    private int straightTicks;
    private int ticksSinceFirstPlacement;
    /**
     * Blocks placed since the key was pressed or the line was restarted, looking at one of them again while holding the
     * key makes it the start of a new line.
     */
    private final Set<BlockPos> placedBlocks = new HashSet<>();
    @Nullable
    private InteractionHand rememberedHand;
    /**
     * The block the crosshair was on last tick.
     */
    @Nullable
    private BlockPos previousTarget;

    public EventResultHolder<InteractionResult> onUseBlock(Player player, Level level, InteractionHand interactionHand, BlockHitResult hitResult) {

        if (level.isClientSide) {

            // using block place context allows for supporting e.g. placing slabs inside other slabs
            // this logic seems to work ideal for our use-case, with Block::canBeReplaced called internally
            BlockPlaceContext context = new BlockPlaceContext(player,
                    interactionHand,
                    player.getItemInHand(interactionHand),
                    hitResult
            );
            this.setNewBlockPos(context.getClickedPos());
            this.interactionHand = interactionHand;
        }

        return EventResultHolder.pass();
    }

    /**
     * Resets everything after a block was placed in mid-air. The block is only remembered, it does not become the start
     * of a fast placement line yet, that only happens when the player looks away from it and back at it while holding
     * the use key, like in Bedrock Edition.
     */
    public void rememberLastPlacement(InteractionHand hand) {
        BlockPos placedPos = this.getNewBlockPos();
        this.clear();
        if (placedPos == null) return;
        this.placedBlocks.add(placedPos.immutable());
        this.rememberedHand = hand;
        // the crosshair is on the placed block right now, so it has to leave it first
        this.previousTarget = placedPos.immutable();
    }

    @Override
    protected void afterNewPosition(Minecraft minecraft) {

        if (minecraft.player == null) return;

        if (this.blockPos != null && this.placedBlocks.size() < 256) {
            this.placedBlocks.add(this.blockPos.immutable());
        }

        // looking at a block that was placed earlier while holding the key and not bridging yet registers it as the
        // new start, but only after looking away from it first, so looking away and back (also over other blocks)
        // and then walking forward continues the bridge
        BlockPos currentTarget = null;
        if (minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHitResult = (BlockHitResult) minecraft.hitResult;
            currentTarget = blockHitResult.getBlockPos().immutable();
            if (this.direction == null && !currentTarget.equals(this.previousTarget) &&
                    !currentTarget.equals(this.blockPos) && this.placedBlocks.contains(currentTarget)) {
                this.blockPos = currentTarget;
                this.hitLocation = blockHitResult.getLocation();
                if (this.interactionHand == null) {
                    this.interactionHand = this.rememberedHand != null ? this.rememberedHand : InteractionHand.MAIN_HAND;
                }
                this.candidateDirection = null;
                this.straightTicks = 0;
                this.ticksSinceFirstPlacement = 0;
            }
        }
        this.previousTarget = currentTarget;

        // nothing placed yet, vanilla handles the first click
        if (this.blockPos == null) return;

        // walking straight while holding the use key starts fast placement in the walking direction right away, no
        // matter where the second block would have been placed, so bridging by walking forward or backward works
        Vec3 movement = new Vec3(minecraft.player.getX() - minecraft.player.xo,
                0.0,
                minecraft.player.getZ() - minecraft.player.zo
        );
        Direction direction = getStraightDirection(movement);
        if (direction == null) {
            this.candidateDirection = null;
            this.straightTicks = 0;
        } else if (direction != this.candidateDirection) {
            this.candidateDirection = direction;
            this.straightTicks = 1;
        } else {
            this.straightTicks++;
        }

        // The direction derived from the first two placed blocks is whatever vanilla happened to place the second
        // block against, which is usually the side facing the player. When walking forward the placement has to go
        // the way the player is walking instead, so it is in front of the player and not behind.
        // Walking backwards or standing still keeps the direction as it is.
        if (this.straightTicks >= STRAIGHT_TICKS_BEFORE_START && this.hitLocation != null &&
                isMovingForward(minecraft.player, movement)) {
            this.direction = this.candidateDirection;
        }
    }

    /**
     * @return true when the horizontal movement goes the way the player is looking, strafing and walking backwards do
     *         not count
     */
    private static boolean isMovingForward(Player player, Vec3 movement) {
        Vec3 look = new Vec3(player.getLookAngle().x(), 0.0, player.getLookAngle().z());
        double lookLength = look.length();
        double movementLength = movement.length();
        if (lookLength < 1.0E-4 || movementLength < 1.0E-4) return false;
        return look.dot(movement) / (lookLength * movementLength) >= MIN_FORWARD_ALIGNMENT;
    }

    @Override
    protected void tickNonActive(Minecraft minecraft) {
        // store hit location once when locking placement direction, so that it is easier to place e.g. stairs consistently
        // looking away from the blocks (at the air) keeps the last location, so looking back and walking still works
        if (minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.BLOCK) {
            this.hitLocation = minecraft.hitResult.getLocation();
        }

        // nothing placed yet, vanilla handles the first click
        if (this.blockPos == null || minecraft.player == null) return;

        if (this.hitLocation == null) {
            this.hitLocation = Vec3.atCenterOf(this.blockPos);
        }

        this.ticksSinceFirstPlacement++;

        if (this.straightTicks >= STRAIGHT_TICKS_BEFORE_START && this.hitLocation != null) {
            this.direction = this.candidateDirection;
            return;
        }

        // not walking straight: hold back vanilla from placing the next block for a while, so there is time to start
        // walking before the placement direction would be decided by where the second block ends up
        int standingDelayTicks = (int) Math.ceil(
                Math.max(0, BedrockBlockPlacement.CONFIG.get(ClientConfig.class).fastPlacementStandingDelayMs) /
                        MILLISECONDS_PER_TICK);
        if (this.ticksSinceFirstPlacement < standingDelayTicks) {
            ((MinecraftAccessor) minecraft).bedrockblockplacement$setRightClickDelay(2);
        }
    }

    @Override
    protected void tickWhenActive(Minecraft minecraft) {
        // always set this to default delay for blocking vanilla from running Minecraft::startUseItem
        ((MinecraftAccessor) minecraft).bedrockblockplacement$setRightClickDelay(4);
        if (BlockClippingHelper.isBlockPositionInLine(minecraft.cameraEntity, minecraft.player.blockInteractionRange(), this.getTargetPosition())) {

            Vec3 hitLocation = new Vec3(this.blockPos.getX() + Mth.frac(this.hitLocation.x()),
                    this.blockPos.getY() + Mth.frac(this.hitLocation.y()),
                    this.blockPos.getZ() + Mth.frac(this.hitLocation.z())
            );
            BlockHitResult hitResult = new BlockHitResult(hitLocation, this.direction, this.blockPos, false);
            ReachAroundPlacementHandler.startUseItemWithSecondaryUseActive(minecraft,
                    minecraft.player,
                    this.interactionHand,
                    hitResult
            );
        }
    }

    /**
     * @return the horizontal axis direction the movement mostly goes in, or null if standing still or too diagonal
     */
    @Nullable
    private static Direction getStraightDirection(Vec3 movement) {
        double length = movement.length();
        if (length < MIN_MOVEMENT) return null;
        double absX = Math.abs(movement.x);
        double absZ = Math.abs(movement.z);
        if (Math.max(absX, absZ) < length * MIN_STRAIGHTNESS) return null;
        if (absX > absZ) {
            return movement.x > 0.0 ? Direction.EAST : Direction.WEST;
        } else {
            return movement.z > 0.0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    @Override
    public boolean isActive() {
        // the hand is needed for placing, it is missing when the state was reset right after a placement
        return super.isActive() && this.hitLocation != null && this.interactionHand != null;
    }

    @Override
    public void clear() {
        super.clear();
        this.hitLocation = null;
        this.interactionHand = null;
        this.candidateDirection = null;
        this.straightTicks = 0;
        this.ticksSinceFirstPlacement = 0;
        this.placedBlocks.clear();
        this.rememberedHand = null;
        this.previousTarget = null;
    }

    @Override
    protected KeyMapping getKeyMapping(Options options) {
        return options.keyUse;
    }

    @Override
    protected boolean requireEmptyBlock() {
        return false;
    }
}
