package org.etwas.streamtweaks.twitch.api;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public record SubscriptionResult(
                @SerializedName("data") List<Subscription> subscriptions) {
}
