package com.niceninjapro.bedrockblockplacement.client;

import com.niceninjapro.bedrockblockplacement.client.handler.FastBreakingHandler;
import com.niceninjapro.bedrockblockplacement.client.handler.FastPlacementHandler;
import com.niceninjapro.bedrockblockplacement.client.handler.KeyBindingHandler;
import com.niceninjapro.bedrockblockplacement.client.handler.ReachAroundPlacementHandler;
import fuzs.puzzleslib.api.client.core.v1.ClientModConstructor;
import fuzs.puzzleslib.api.client.core.v1.context.KeyMappingsContext;
import fuzs.puzzleslib.api.client.event.v1.ClientTickEvents;
import fuzs.puzzleslib.api.client.event.v1.entity.player.InteractionInputEvents;
import fuzs.puzzleslib.api.event.v1.LoadCompleteCallback;
import fuzs.puzzleslib.api.event.v1.entity.player.PlayerInteractEvents;

public class BedrockBlockPlacementClient implements ClientModConstructor {

    @Override
    public void onConstructMod() {
        registerEventHandlers();
    }

    private static void registerEventHandlers() {
        PlayerInteractEvents.USE_BLOCK.register(FastPlacementHandler.INSTANCE::onUseBlock);
        InteractionInputEvents.USE.register(ReachAroundPlacementHandler::onUseInteraction);
        ClientTickEvents.START.register(ReachAroundPlacementHandler::onStartClientTick);
        ClientTickEvents.START.register(FastPlacementHandler.INSTANCE::onStartClientTick);
        ClientTickEvents.START.register(FastBreakingHandler.INSTANCE::onStartClientTick);
        PlayerInteractEvents.ATTACK_BLOCK.register(FastBreakingHandler.INSTANCE::onAttackBlock);
        LoadCompleteCallback.EVENT.register(KeyBindingHandler::onLoadComplete);
    }

    @Override
    public void onRegisterKeyMappings(KeyMappingsContext context) {
        KeyBindingHandler.onRegisterKeyMappings(context);
    }
}
