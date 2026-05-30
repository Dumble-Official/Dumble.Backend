using Dumble.RecommendationService.Domain.Outbox;

namespace Dumble.RecommendationService.Application.Features.Interactions;

/// <summary>
/// Maps the client's behaviour event-type string to an <see cref="InteractionSignal"/>.
/// Only client-only signals are accepted over HTTP — reaction/comment/share arrive from
/// domain events (Channel 2), so reporting them here would double-count.
/// </summary>
public static class ClientSignalMapper
{
    public static InteractionSignal Parse(string eventType) =>
        eventType?.Trim().ToLowerInvariant() switch
        {
            "view" => InteractionSignal.View,
            "click" => InteractionSignal.Click,
            "timespent" => InteractionSignal.Dwell,
            _ => throw new ArgumentException(
                $"Unsupported client behaviour event type '{eventType}'. Allowed: View, Click, TimeSpent.",
                nameof(eventType))
        };
}
