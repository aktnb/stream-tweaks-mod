package org.etwas.streamtweaks.config;

import org.etwas.streamtweaks.StreamTweaks;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = StreamTweaks.MOD_ID)
public class StreamTweaksConfig implements ConfigData {
    public boolean showStreamChat = true;

    /**
     * Automatically start Twitch authentication when joining a world if not already
     * authenticated.
     * Displays a clickable chat message for easy authentication.
     */
    public boolean autoAuthOnWorldJoin = true;
}
