package com.niceninjapro.bedrockblockplacement.neoforge;

import com.niceninjapro.bedrockblockplacement.BedrockBlockPlacement;
import fuzs.puzzleslib.api.core.v1.ModConstructor;
import net.neoforged.fml.common.Mod;

@Mod(BedrockBlockPlacement.MOD_ID)
public class BedrockBlockPlacementNeoForge {

    public BedrockBlockPlacementNeoForge() {
        ModConstructor.construct(BedrockBlockPlacement.MOD_ID, BedrockBlockPlacement::new);
    }
}
