package com.chattriggers.ctjs.mixins;

import com.chattriggers.ctjs.CTClientCommandSource;
import net.minecraft.client.network.ClientCommandSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(ClientCommandSource.class)
public abstract class ClientCommandSourceMixin implements CTClientCommandSource {
    @Unique
    private final HashMap<String, Object> contextValues = new HashMap<>();

    @Override
    public void setContextValue(@NotNull String key, @NotNull Object value) {
        contextValues.put(key, value);
    }

    @Override
    public void appendContextValue(@NotNull String key, @NotNull Object value) {
        Object existing = contextValues.get(key);
        if (existing == null) {
            existing = new ArrayList<>();
            setContextValue(key, existing);
        } else if (!(existing instanceof List<?>)) {
            throw new IllegalArgumentException(
                "Context key \"" + key + "\" is not a list! Use setContextValue() instead"
            );
        }

        //noinspection unchecked
        ((List<Object>) existing).add(value);
    }

    @Override
    @NotNull
    public HashMap<String, Object> getContextValues() {
        return contextValues;
    }

    @Override
    public Object getContextValue(String key) {
        return contextValues.get(key);
    }

    @Override
    public List<Object> getContextListValue(String key) {
        Object value = getContextValue(key);
        if (!(value instanceof List<?>)) {
            throw new IllegalArgumentException(
                "Context key \"" + key + "\" is not a list! Use getContextValue() instead"
            );
        }

        //noinspection unchecked
        return (List<Object>) value;
    }
}
