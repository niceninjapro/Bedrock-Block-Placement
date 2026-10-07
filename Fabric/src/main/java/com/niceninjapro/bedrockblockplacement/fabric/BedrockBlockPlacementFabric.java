package com.niceninjapro.bedrockblockplacement.fabric;

import com.niceninjapro.bedrockblockplacement.BedrockBlockPlacement;
import fuzs.puzzleslib.api.core.v1.ModConstructor;
import net.fabricmc.api.ModInitializer;

public class BedrockBlockPlacementFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ModConstructor.construct(BedrockBlockPlacement.MOD_ID, BedrockBlockPlacement::new);
    }
}
