package org.etwas.streamtweaks;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamTweaks implements ModInitializer {

    public static final String MOD_ID = "stream-tweaks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Stream Tweaks initialized");
    }
}
