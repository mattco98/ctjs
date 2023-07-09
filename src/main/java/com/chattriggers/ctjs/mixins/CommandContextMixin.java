package com.chattriggers.ctjs.mixins;

import com.mojang.brigadier.context.CommandContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CommandContext.class)
public abstract class CommandContextMixin {
    @Shadow public abstract <V> V getArgument(String name, Class<V> clazz);

    @Unique
    public Object get(String key) {
        return getArgument(key, Object.class);
    }
}
