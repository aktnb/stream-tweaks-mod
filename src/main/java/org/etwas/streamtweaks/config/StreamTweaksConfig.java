package org.etwas.streamtweaks.config;

import org.etwas.streamtweaks.StreamTweaks;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = StreamTweaks.MOD_ID)
public class StreamTweaksConfig implements ConfigData {
    public boolean showStreamChat = true;
}
