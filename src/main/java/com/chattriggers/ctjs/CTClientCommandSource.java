package com.chattriggers.ctjs;

import net.minecraft.command.CommandSource;

import java.util.HashMap;
import java.util.List;

public interface CTClientCommandSource extends CommandSource {
    void setContextValue(String key, Object value);

    void appendContextValue(String key, Object value);

    HashMap<String, Object> getContextValues();

    Object getContextValue(String key);

    List<Object> getContextListValue(String key);
}
