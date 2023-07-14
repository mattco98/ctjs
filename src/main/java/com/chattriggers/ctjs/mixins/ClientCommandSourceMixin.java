package com.chattriggers.ctjs.mixins;

import com.chattriggers.ctjs.CTClientCommandSource;
import net.minecraft.client.network.ClientCommandSource;
import org.jetbrains.annotations.NotNull;
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
    public void setValue(@NotNull String key, @NotNull Object value) {
        contextValues.put(key, value);
    }

    @Override
    public void appendValue(@NotNull String key, @NotNull Object value) {
        Object existing = contextValues.get(key);
        if (existing == null) {
            existing = new ArrayList<>();
            setValue(key, existing);
        } else if (!(existing instanceof List<?>)) {
            throw new IllegalStateException();
        }

        //noinspection unchecked
        ((List<Object>) existing).add(value);
    }

    @Override
    @NotNull
    public HashMap<String, Object> getValues() {
        return contextValues;
    }
}
