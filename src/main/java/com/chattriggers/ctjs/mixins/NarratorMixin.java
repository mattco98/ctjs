package com.chattriggers.ctjs.mixins;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.mojang.text2speech.Narrator;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Narrator.class)
public interface NarratorMixin {
    @WrapWithCondition(
        method = "getNarrator",
        at = @At(
            value = "INVOKE",
            target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Throwable;)V"
        ),
        remap = false
    )
    private static boolean removeLoggingHugeAnnoyingError(Logger logger, String fmt, Throwable error) {
        return false;
    }
}
