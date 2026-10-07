package com.niceninjapro.bedrockblockplacement.client.handler;

import com.niceninjapro.bedrockblockplacement.BedrockBlockPlacement;
import com.niceninjapro.bedrockblockplacement.client.util.BlockClippingHelper;
import com.niceninjapro.bedrockblockplacement.config.ClientConfig;
import fuzs.puzzleslib.api.event.v1.core.EventResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ReachAroundPlacementHandler {
    /**
     * Ticks the use key has been held down in a row, 1 on the tick the key was pressed, 0 while not pressed.
     */
    private static int useKeyHeldTicks;
    private static boolean isProcessingInteraction;

    public static void onStartClientTick(Minecraft minecraft) {
        useKeyHeldTicks = minecraft.options.keyUse.isDown() ? useKeyHeldTicks + 1 : 0;
    }

    public static EventResult onUseInteraction(Minecraft minecraft, LocalPlayer player, InteractionHand interactionHand, HitResult hitResult) {

        if (!BedrockBlockPlacement.CONFIG.get(ClientConfig.class).allowReachAroundPlacement) return EventResult.PASS;

        // reach-around placement only happens on a click, not when vanilla repeats the placement while the use key is
        // held down, otherwise looking at the air next to a block while holding the key keeps placing blocks and
        // resets the fast placement state; holding the key is what the fast placement line lock is for
        if (useKeyHeldTicks > 1) return EventResult.PASS;

        // we need to guard this as the Fabric event fires from MultiPlayerGameMode::useItemOn which we call later
        if (!isProcessingInteraction && !FastPlacementHandler.INSTANCE.isActive()) {

            BlockHitResult blockHitResult = getReachAroundHitResult(minecraft, player, hitResult);
            if (blockHitResult != null) {

                isProcessingInteraction = true;
                InteractionResult interactionResult = startUseItemWithSecondaryUseActive(minecraft,
                        player,
                        interactionHand,
                        blockHitResult
                );
                isProcessingInteraction = false;
                // the block placed in mid-air does not start a fast placement line right away, only looking away and back
                // at it while holding the use key does, like in Bedrock Edition
                FastPlacementHandler.INSTANCE.rememberLastPlacement(interactionHand);

                if (interactionResult.consumesAction()) {

                    return EventResult.INTERRUPT;
                }
            }
        }

        return EventResult.PASS;
    }

    @Nullable
    private static BlockHitResult getReachAroundHitResult(Minecraft minecraft, LocalPlayer player, HitResult hitResult) {

        if (hitResult.getType() == HitResult.Type.MISS && player.getViewXRot(0.0F) >= 45.0F) {

            // allow for reach around placing while hovering over a block, so does Bedrock Edition, too
            BlockPos blockPos = player.onGround() ? player.getOnPos() : player.blockPosition().below();
            // don't allow this while flying or falling in midair, think like we need a block to place against (although technically speaking it's not necessary)
            if (!minecraft.level.isEmptyBlock(blockPos)) {

                Direction direction = player.getDirection();
                BlockPos targetPos = blockPos.relative(direction);
                if (BlockClippingHelper.isBlockPositionInLine(player, player.blockInteractionRange(), targetPos)) {

                    Vec3i directionNormal = direction.getNormal();
                    Vec3 hitLocation = new Vec3(directionNormal.getX(), directionNormal.getY(), directionNormal.getZ());
                    // Bedrock Edition places slabs and stairs upside down, by using max for y we achieve the same behavior
                    VoxelShape voxelShape = minecraft.level.getBlockState(blockPos)
                            .getCollisionShape(minecraft.level, blockPos, CollisionContext.of(player));
                    double maxY = voxelShape.max(Direction.Axis.Y);
                    double minY = voxelShape.min(Direction.Axis.Y);
                    double y;
                    // will be infinite for empty shapes
                    if (Double.isInfinite(minY) || Double.isInfinite(maxY)) {
                        y = Math.abs((Mth.frac(player.position().y()) - 0.5) * 2);
                    } else {
                        y = Mth.lerp(0.75, minY, maxY);
                    }
                    hitLocation = hitLocation.scale(0.5).add(0.5, y, 0.5).add(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                    return new BlockHitResult(hitLocation, direction, blockPos, false);
                }
            }
        }

        return null;
    }

    public static InteractionResult startUseItemWithSecondaryUseActive(Minecraft minecraft, LocalPlayer player, InteractionHand interactionHand, BlockHitResult blockHitResult) {

        // allow blocks that have an interaction to be placed using the fast placement mechanic, e.g. fence gates and chests
        boolean shiftKeyDown =
                minecraft.player.input.shiftKeyDown || !BedrockBlockPlacement.CONFIG.get(ClientConfig.class).bypassUseBlock;
        if (!shiftKeyDown) {
            minecraft.player.input.shiftKeyDown = true;
            minecraft.player.connection.send(new ServerboundPlayerCommandPacket(minecraft.player,
                    ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY
            ));
        }

        InteractionResult interactionResult = startUseItem(minecraft, player, interactionHand, blockHitResult);

        if (!shiftKeyDown) {
            minecraft.player.input.shiftKeyDown = false;
            minecraft.player.connection.send(new ServerboundPlayerCommandPacket(minecraft.player,
                    ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY
            ));
        }

        return interactionResult;
    }

    public static InteractionResult startUseItem(Minecraft minecraft, LocalPlayer player, InteractionHand interactionHand, BlockHitResult blockHitResult) {

        ItemStack itemInHand = player.getItemInHand(interactionHand);
        int itemCount = itemInHand.getCount();
        InteractionResult interactionResult = minecraft.gameMode.useItemOn(player, interactionHand, blockHitResult);

        if (interactionResult.shouldSwing()) {
            player.swing(interactionHand);
            if (!itemInHand.isEmpty() &&
                    (itemInHand.getCount() != itemCount || minecraft.gameMode.hasInfiniteItems())) {
                minecraft.gameRenderer.itemInHandRenderer.itemUsed(interactionHand);
            }
        }

        return interactionResult;
    }
}
