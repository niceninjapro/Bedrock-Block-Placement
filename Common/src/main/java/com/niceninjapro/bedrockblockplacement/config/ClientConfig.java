package com.niceninjapro.bedrockblockplacement.config;

import fuzs.puzzleslib.api.config.v3.Config;
import fuzs.puzzleslib.api.config.v3.ConfigCore;
import fuzs.puzzleslib.api.config.v3.serialization.ConfigDataSet;
import fuzs.puzzleslib.api.config.v3.serialization.KeyedValueProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

public class ClientConfig implements ConfigCore {
    @Config(
            description = {
                    "Allow using Bedrock Edition-like fast block placement, with blocks being placed without leaving gaps or unwanted placements. Also enables placing blocks when clicking in mid-air while building.",
                    "Additionally introduces a similar mechanic for quickly breaking blocks in a row or column in creative mode.",
                    "Toggle in-game via the dedicated key binding."
            }
    )
    public boolean allowFastPlacement = true;
    @Config(
            description = {
                    "Delay in game ticks between two blocks being broken while holding the attack key in creative mode (20 ticks are one second).",
                    "5 is the vanilla delay, 0 breaks a block every game tick. Only applies while the fast breaking line lock is not active."
            }
    )
    @Config.IntRange(min = 0, max = 5)
    public int defaultBreakingDelayTicks = 3;
    @Config(
            description = {
                    "Time in milliseconds the player has to move in one straight direction while holding the attack key in creative mode, before fast breaking starts and locks to a line.",
                    "Rounded up to game ticks (50 milliseconds each), so 100 is two ticks."
            }
    )
    @Config.IntRange(min = 0, max = 2000)
    public int fastBreakingDelayMs = 100;
    @Config(
            description = {
                    "Maximum angle in degrees between the looking direction and the direction of the fast breaking line.",
                    "Fast breaking is released when looking further away from the line than this."
            }
    )
    @Config.IntRange(min = 1, max = 180)
    public int fastBreakingMaxLookAngle = 40;
    @Config(
            description = {
                    "Switches the check that releases the fast breaking line lock from the looking angle to a face check.",
                    "The lock then is only kept while the crosshair is on a full block in the line being broken. Blocks that are not full, like grass, are ignored by the check."
            }
    )
    public boolean fastBreakingFaceCheck = true;
    @Config(
            description = {
                    "Mining speed to movement speed ratio while NOT locked to a straight line: how many blocks are broken for every block the player travels (also while flying).",
                    "The faster the player moves, the faster blocks are broken, never slower than the default breaking delay. 0 turns this off, 1 is one block per block travelled."
            }
    )
    @Config.DoubleRange(min = 0.0, max = 5.0)
    public double defaultBreakingSpeedRatio = 2.0;
    @Config(
            description = {
                    "Mining speed to movement speed ratio while locked to a straight line: how many blocks are broken for every block the player travels along the line.",
                    "1 is one block per block travelled, higher values break blocks faster than the player moves (at most one block per game tick)."
            }
    )
    @Config.DoubleRange(min = 0.1, max = 5.0)
    public double lockedBreakingSpeedRatio = 1.0;
    @Config(
            description = {
                    "Time in milliseconds after placing the first block with the use key held down, before the next block is placed while the player is standing still.",
                    "Gives time to start walking, which starts fast placement in the walking direction right away. The vanilla delay is already 200 milliseconds, so values below that change nothing."
            }
    )
    @Config.IntRange(min = 0, max = 2000)
    public int fastPlacementStandingDelayMs = 300;
    @Config(description = "Allow using Bedrock Edition-like reach-around block placement, where a block can be placed directly in front of the block the player is standing on when clicking in mid-air for fast bridging. Only happens on a click, holding the use key does not repeat it.")
    public boolean allowReachAroundPlacement = true;
    @Config(description = "Treat sneaking as active while placing blocks via the fast placement mechanic or reach-around. Allows block placement to work with interactable blocks such as chests and fence gates.")
    public boolean bypassUseBlock = false;
    @Config(name = "normal_placement_blocks",
            description = {
                    "Blocks that are excluded from the fast placement mechanic.",
                    ConfigDataSet.CONFIG_DESCRIPTION
            }
    )
    List<String> normalPlacementRaw = KeyedValueProvider.toString(Registries.BLOCK, Blocks.SCAFFOLDING);

    public ConfigDataSet<Block> normalPlacement;

    @Override
    public void afterConfigReload() {
        this.normalPlacement = ConfigDataSet.from(Registries.BLOCK, this.normalPlacementRaw);
    }
}
