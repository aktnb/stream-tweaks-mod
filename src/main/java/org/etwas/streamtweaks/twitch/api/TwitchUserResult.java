package org.etwas.streamtweaks.twitch.api;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public record TwitchUserResult(
                @SerializedName("data") List<TwitchUser> users) {

}
