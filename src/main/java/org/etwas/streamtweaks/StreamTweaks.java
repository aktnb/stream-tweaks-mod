package org.etwas.streamtweaks;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamTweaks implements ModInitializer {

    public static final String MOD_ID = "stream-tweaks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Stream Tweaks initialized");
    }

    public static void devLogger(String loggerInput) {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        LOGGER.info("DEV - [ %s ]".formatted(loggerInput));
    }
}
