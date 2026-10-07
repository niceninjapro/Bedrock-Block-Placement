package com.niceninjapro.bedrockblockplacement.client.handler;

import com.niceninjapro.bedrockblockplacement.BedrockBlockPlacement;
import com.niceninjapro.bedrockblockplacement.config.ClientConfig;
import com.niceninjapro.bedrockblockplacement.mixin.client.accessor.MultiPlayerGameModeAccessor;
import fuzs.puzzleslib.api.event.v1.core.EventResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Bedrock Edition-like creative mode block breaking.
 * <p>
 * Holding the attack key breaks the block under the crosshair with a configurable delay (vanilla by default) until
 * the player has moved in one straight direction, while looking roughly that way, for a short time (configurable, 100
 * ms by default). Then a straight line through the last broken block, along that direction, is locked in. From then
 * on only the block under the crosshair is ever broken, and only if it is a full block on that line, at the rate the
 * player is moving (scaled by the locked mining speed ratio, one block for every block travelled by default). Full blocks beside, above or below the line are left alone,
 * while anything that is not a full block (grass, flowers, slabs, ...) is not affected by the lock at all.
 * <p>
 * Outside of a line lock the delay between breaks still shrinks with the player's speed (unlocked mining speed ratio).
 * <p>
 * The lock is released when the player looks away from the line (crosshair leaves the line by default, a looking angle
 * if the face check is disabled), or after standing still for a moment. The next straight movement then starts a new line from the last broken block, so turning
 * corners works.
 */
public class FastBreakingHandler {
    public static final FastBreakingHandler INSTANCE = new FastBreakingHandler();
    /**
     * Blocks per tick the player has to move to count as moving (walking is about 0.2, creative flying about 0.5).
     */
    private static final double MIN_MOVEMENT_SQR = 0.1 * 0.1;
    /**
     * How much of the movement has to go in one axis direction to count as moving straight (0.8 allows for about 35
     * degrees off the axis).
     */
    private static final double MIN_STRAIGHTNESS = 0.8;
    private static final double MILLISECONDS_PER_TICK = 50.0;
    /**
     * Ticks of standing still before the movement direction lock is released.
     */
    private static final int STILL_TICKS_BEFORE_UNLOCK = 10;
    /**
     * Vanilla delay between two creative breaks, used to keep vanilla from breaking blocks outside our line while locked.
     */
    private static final int VANILLA_CREATIVE_DESTROY_DELAY = 5;
    private static final double MAX_STORED_DISTANCE = 2.0;
    /**
     * Blocks per tick (after applying the speed ratio) below which the speed based breaking is ignored, so tiny
     * movements do not matter.
     */
    private static final double MIN_SPEED_FOR_SCALING = 0.05;

    /**
     * The last block that was broken, the line is going through it when locking.
     */
    @Nullable
    private BlockPos lastPos;
    /**
     * The block the crosshair was on last tick, used as a fallback to find the first broken block.
     */
    @Nullable
    private BlockPos lastSeenTarget;
    @Nullable
    private Direction candidateDirection;
    private int straightTicks;
    @Nullable
    private Direction lockDirection;
    @Nullable
    private BlockPos lineOrigin;
    private double distanceBudget;
    private int stillTicks;

    public EventResult onAttackBlock(Player player, Level level, InteractionHand interactionHand, BlockPos pos, Direction direction) {

        // the first block broken after pressing the attack key is where our line starts
        if (level.isClientSide && player.getAbilities().instabuild && this.lastPos == null) {
            this.lastPos = pos.immutable();
        }

        return EventResult.PASS;
    }

    public void onStartClientTick(Minecraft minecraft) {

        LocalPlayer player = minecraft.player;
        if (!KeyBindingHandler.isFastPlacementActive() || !minecraft.options.keyAttack.isDown() || player == null
                || minecraft.gameMode == null || minecraft.level == null || !player.getAbilities().instabuild) {

            this.clear();
            return;
        }

        ClientConfig config = BedrockBlockPlacement.CONFIG.get(ClientConfig.class);
        Vec3 movement = new Vec3(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo);
        if (!this.tickLineBreaking(minecraft, player, config, movement)) {
            this.tickDefaultBreaking(minecraft, config, movement);
        }
    }

    /**
     * Makes holding the attack key break blocks faster than vanilla if configured. The faster the player moves (e.g.
     * flying), the shorter the delay gets, so blocks are broken at a rate based on the speed even without a line lock.
     */
    private void tickDefaultBreaking(Minecraft minecraft, ClientConfig config, Vec3 movement) {
        MultiPlayerGameModeAccessor accessor = (MultiPlayerGameModeAccessor) minecraft.gameMode;
        int delay = Math.min(VANILLA_CREATIVE_DESTROY_DELAY, Math.max(0, config.defaultBreakingDelayTicks));

        double speed = movement.length();
        double blocksPerTick = speed * Math.max(0.0, config.defaultBreakingSpeedRatio);
        if (blocksPerTick >= MIN_SPEED_FOR_SCALING) {
            // a delay of n ticks means one block every n + 1 ticks
            int speedDelay = Math.max(0, (int) Math.ceil(1.0 / blocksPerTick) - 1);
            delay = Math.min(delay, speedDelay);
        }

        // only ever lower the delay, vanilla counts it down on its own and sets it back to 5 after every break
        if (accessor.bedrockblockplacement$getDestroyDelay() > delay) {
            accessor.bedrockblockplacement$setDestroyDelay(delay);
        }
    }

    /**
     * @return true when the line lock is active and is handling block breaking this tick
     */
    private boolean tickLineBreaking(Minecraft minecraft, LocalPlayer player, ClientConfig config, Vec3 movement) {

        // fallback in case the attack event did not give us the first block: the block we were looking at last tick
        if (this.lastPos == null && this.lastSeenTarget != null) {
            this.lastPos = this.lastSeenTarget;
        }
        BlockPos seenTarget = null;
        if (minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.BLOCK) {
            seenTarget = ((BlockHitResult) minecraft.hitResult).getBlockPos();
        }
        this.lastSeenTarget = seenTarget;

        // nothing broken yet, vanilla handles the first click
        if (this.lastPos == null) return false;

        if (movement.lengthSqr() < MIN_MOVEMENT_SQR) {

            // standing still: vanilla breaking applies as usual, only a short pause while locked to a line keeps
            // vanilla from breaking blocks outside the line
            this.candidateDirection = null;
            this.straightTicks = 0;
            if (++this.stillTicks >= STILL_TICKS_BEFORE_UNLOCK) {
                this.unlock();
            }
            if (this.lockDirection != null && !isNonFullBlockTarget(minecraft)) {
                ((MultiPlayerGameModeAccessor) minecraft.gameMode).bedrockblockplacement$setDestroyDelay(VANILLA_CREATIVE_DESTROY_DELAY);
                return true;
            }
            return false;
        }

        this.stillTicks = 0;
        // we run at the beginning of the client tick, so the crosshair target is not updated yet
        // there does not seem to exist a better hook after this is updated, but before keybindings are processed
        minecraft.gameRenderer.pick(1.0F);
        if (this.lockDirection == null) {

            // only start once the player has moved straight in the same direction, while looking that way, for a while
            Direction direction = getStraightDirection(movement);
            if (direction != null && !isLookingAtLine(minecraft, player, config, direction, this.lastPos)) {
                direction = null;
            }
            if (direction == null) {
                this.candidateDirection = null;
                this.straightTicks = 0;
            } else if (direction != this.candidateDirection) {
                this.candidateDirection = direction;
                this.straightTicks = 1;
            } else {
                this.straightTicks++;
            }

            int ticksBeforeLock = Math.max(1, (int) Math.ceil(Math.max(0, config.fastBreakingDelayMs) / MILLISECONDS_PER_TICK));
            if (this.straightTicks < ticksBeforeLock) return false;

            this.lockDirection = this.candidateDirection;
            this.lineOrigin = this.lastPos;
            // allows breaking the first block right away
            this.distanceBudget = 1.0;
        } else if (!isLookingAtLine(minecraft, player, config, this.lockDirection, this.lineOrigin)) {

            // looking too far away from the line, release the lock and let vanilla breaking take over again
            this.unlock();
            return false;
        }

        MultiPlayerGameModeAccessor accessor = (MultiPlayerGameModeAccessor) minecraft.gameMode;

        // the movement budget grows with the distance travelled in the locked direction, scaled by the configured
        // mining speed to movement speed ratio (one block broken per block moved by default)
        Direction lockDirection = this.lockDirection;
        double alongLine = movement.x * lockDirection.getStepX() + movement.y * lockDirection.getStepY() + movement.z * lockDirection.getStepZ();
        double ratio = Math.max(0.0, config.lockedBreakingSpeedRatio);
        this.distanceBudget = Math.min(MAX_STORED_DISTANCE, this.distanceBudget + Math.max(0.0, alongLine) * ratio);

        // the lock only controls full blocks, everything else (grass, flowers, slabs, ...) is left to normal breaking
        // so it can be cleared out of the way like usual
        if (isNonFullBlockTarget(minecraft)) return false;

        // by default keep vanilla from breaking whatever the crosshair is on
        accessor.bedrockblockplacement$setDestroyDelay(VANILLA_CREATIVE_DESTROY_DELAY);
        if (this.distanceBudget >= 1.0 && minecraft.hitResult != null
                && minecraft.hitResult.getType() == HitResult.Type.BLOCK) {

            BlockPos targetPos = ((BlockHitResult) minecraft.hitResult).getBlockPos();
            if (this.isOnLine(targetPos)) {
                // let vanilla break the block the crosshair is on right now, ignores Minecraft::missTime as it does
                // not apply for creative mode
                accessor.bedrockblockplacement$setDestroyDelay(0);
                this.lastPos = targetPos.immutable();
                this.distanceBudget -= 1.0;
            }
        }

        return true;
    }

    private boolean isOnLine(BlockPos pos) {
        return isOnLine(this.lockDirection, this.lineOrigin, pos);
    }

    private static boolean isOnLine(Direction direction, BlockPos origin, BlockPos pos) {
        return switch (direction.getAxis()) {
            case X -> pos.getY() == origin.getY() && pos.getZ() == origin.getZ();
            case Y -> pos.getX() == origin.getX() && pos.getZ() == origin.getZ();
            case Z -> pos.getX() == origin.getX() && pos.getY() == origin.getY();
        };
    }

    /**
     * Decides if the player is still aiming at the line, either by looking angle or, if configured, by looking at a
     * full block that is part of the line. Blocks that are not full, like grass, never count against the player.
     */
    private static boolean isLookingAtLine(Minecraft minecraft, LocalPlayer player, ClientConfig config, Direction direction, BlockPos origin) {

        if (!config.fastBreakingFaceCheck) {
            return isLookingAlong(player, direction, config.fastBreakingMaxLookAngle);
        }

        if (minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.BLOCK) return false;
        if (isNonFullBlockTarget(minecraft)) return true;
        return isOnLine(direction, origin, ((BlockHitResult) minecraft.hitResult).getBlockPos());
    }

    private static boolean isNonFullBlockTarget(Minecraft minecraft) {
        if (minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.BLOCK) return false;
        BlockPos pos = ((BlockHitResult) minecraft.hitResult).getBlockPos();
        BlockState blockState = minecraft.level.getBlockState(pos);
        return !Block.isShapeFullBlock(blockState.getShape(minecraft.level, pos));
    }

    private static boolean isLookingAlong(Player player, Direction direction, int maxAngleDegrees) {
        Vec3 normal = Vec3.atLowerCornerOf(direction.getNormal());
        double maxAngle = Math.toRadians(Math.min(180, Math.max(0, maxAngleDegrees)));
        return player.getLookAngle().dot(normal) >= Math.cos(maxAngle);
    }

    /**
     * @return the axis direction the movement mostly goes in, or null if the movement is too diagonal
     */
    @Nullable
    private static Direction getStraightDirection(Vec3 movement) {
        double absX = Math.abs(movement.x);
        double absY = Math.abs(movement.y);
        double absZ = Math.abs(movement.z);
        double dominant = Math.max(absX, Math.max(absY, absZ));
        if (dominant < movement.length() * MIN_STRAIGHTNESS) return null;
        if (dominant == absY) {
            return movement.y > 0.0 ? Direction.UP : Direction.DOWN;
        } else if (dominant == absX) {
            return movement.x > 0.0 ? Direction.EAST : Direction.WEST;
        } else {
            return movement.z > 0.0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private void unlock() {
        this.lockDirection = null;
        this.lineOrigin = null;
        this.candidateDirection = null;
        this.straightTicks = 0;
        this.distanceBudget = 0.0;
    }

    public void clear() {
        this.unlock();
        this.lastPos = null;
        this.lastSeenTarget = null;
        this.stillTicks = 0;
    }
}
