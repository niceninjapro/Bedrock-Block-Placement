package com.niceninjapro.bedrockblockplacement.neoforge.client;

import com.niceninjapro.bedrockblockplacement.BedrockBlockPlacement;
import com.niceninjapro.bedrockblockplacement.client.BedrockBlockPlacementClient;
import com.niceninjapro.bedrockblockplacement.data.client.ModLanguageProvider;
import fuzs.puzzleslib.api.client.core.v1.ClientModConstructor;
import fuzs.puzzleslib.neoforge.api.data.v2.core.DataProviderHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = BedrockBlockPlacement.MOD_ID, dist = Dist.CLIENT)
public class BedrockBlockPlacementNeoForgeClient {

    public BedrockBlockPlacementNeoForgeClient() {
        ClientModConstructor.construct(BedrockBlockPlacement.MOD_ID, BedrockBlockPlacementClient::new);
        DataProviderHelper.registerDataProviders(BedrockBlockPlacement.MOD_ID, ModLanguageProvider::new);
    }
}
