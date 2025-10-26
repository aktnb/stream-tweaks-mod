package org.etwas.streamtweaks.twitch.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.etwas.streamtweaks.StreamTweaks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

import static org.etwas.streamtweaks.StreamTweaks.LOGGER;

public class TwitchCredentialStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve(StreamTweaks.MOD_ID)
            .resolve("twitch-credentials.json");

    public TwitchCredentials loadOrCreate() {
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                return GSON.fromJson(Files.readString(file), TwitchCredentials.class);
            }
        } catch (IOException ignored) {
        }
        return new TwitchCredentials(null, null);
    }

    public void save(TwitchCredentials credentials) {
        try {
            Files.createDirectories(file.getParent());
            var tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(credentials), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            try {
                if (Files.getFileStore(tmp).supportsFileAttributeView("posix")) {
                    Set<PosixFilePermission> perms = EnumSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE);
                    Files.setPosixFilePermissions(tmp, perms);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to set file permissions on {}", tmp, e);
            }
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Twitch credentials saved to {}", file);
        } catch (IOException e) {
            LOGGER.error("Failed to save Twitch credentials to {}", file, e);
        }
    }
}
