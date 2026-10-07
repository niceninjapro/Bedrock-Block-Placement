package com.niceninjapro.bedrockblockplacement.fabric.client;

import com.niceninjapro.bedrockblockplacement.BedrockBlockPlacement;
import com.niceninjapro.bedrockblockplacement.client.BedrockBlockPlacementClient;
import fuzs.puzzleslib.api.client.core.v1.ClientModConstructor;
import net.fabricmc.api.ClientModInitializer;

public class BedrockBlockPlacementFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientModConstructor.construct(BedrockBlockPlacement.MOD_ID, BedrockBlockPlacementClient::new);
    }
}
