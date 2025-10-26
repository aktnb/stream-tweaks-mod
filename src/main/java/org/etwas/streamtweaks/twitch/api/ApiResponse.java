package org.etwas.streamtweaks.twitch.api;

public record ApiResponse<T>(
        boolean isSuccess,
        int statusCode,
        T data,
        String errorMessage) {

}
