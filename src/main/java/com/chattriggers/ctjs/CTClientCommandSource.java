package com.chattriggers.ctjs;

import net.minecraft.command.CommandSource;

import java.util.HashMap;

public interface CTClientCommandSource extends CommandSource {
    void setValue(String key, Object value);

    void appendValue(String key, Object value);

    HashMap<String, Object> getValues();
}
