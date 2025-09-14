package org.etwas.streamtweaks.twitch.auth;

public class AuthResult {
    public final String token;
    public final AuthType authType;

    public AuthResult(String token, AuthType authType) {
        this.token = token;
        this.authType = authType;
    }

    public enum AuthType {
        CACHED_TOKEN,
        NEW_AUTHORIZATION
    }
}