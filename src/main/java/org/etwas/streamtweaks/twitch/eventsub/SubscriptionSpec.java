package org.etwas.streamtweaks.twitch.eventsub;

import java.util.Map;

public record SubscriptionSpec(
    String type,
    String version,
    Map<String, Object> condition
) {}
