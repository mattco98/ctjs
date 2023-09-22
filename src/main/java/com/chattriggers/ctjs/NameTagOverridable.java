package com.chattriggers.ctjs;

import com.chattriggers.ctjs.api.message.TextComponent;
import com.chattriggers.ctjs.typing.annotations.InternalApi;
import org.jetbrains.annotations.Nullable;

@InternalApi
public interface NameTagOverridable {
    void ctjs_setOverriddenNametagName(@Nullable TextComponent component);
}
