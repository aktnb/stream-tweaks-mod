package org.etwas.streamtweaks.twitch.api;

import java.util.List;

public record SubscriptionResult(
        List<Subscription> data) {
}
