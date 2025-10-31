package org.etwas.streamtweaks.twitch.auth;

public record TwitchCredentials(
        String login,
        String accessToken) {
}
