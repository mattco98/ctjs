package com.chattriggers.ctjs;

import com.chattriggers.ctjs.typing.annotations.InternalApi;

@InternalApi
public interface Skippable {
    void ctjs_setShouldSkip(boolean shouldSkip);
}
