package com.niceninjapro.bedrockblockplacement.mixin.client.accessor;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {

    @Accessor("destroyDelay")
    int bedrockblockplacement$getDestroyDelay();

    @Accessor("destroyDelay")
    void bedrockblockplacement$setDestroyDelay(int destroyDelay);
}
