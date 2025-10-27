package org.etwas.streamtweaks.twitch.auth;

public record AuthResult(String token, AuthType authType) {
    public enum AuthType {
        CACHED_TOKEN,
        NEW_AUTHORIZATION,
        NONE
    }
}