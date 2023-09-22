package com.chattriggers.ctjs;

import com.chattriggers.ctjs.typing.annotations.InternalApi;
import net.minecraft.command.CommandSource;

import java.util.HashMap;

@InternalApi
public interface CTClientCommandSource extends CommandSource {
    void setContextValue(String key, Object value);

    HashMap<String, Object> getContextValues();
}
