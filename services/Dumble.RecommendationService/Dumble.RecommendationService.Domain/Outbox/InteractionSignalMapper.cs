namespace Dumble.RecommendationService.Domain.Outbox;

/// <summary>
/// The single place that encodes design decision D13 — how an app behaviour signal
/// becomes a Recombee write. Kept pure and centralised so the policy can be reasoned
/// about and unit-tested in isolation.
/// </summary>
public static class InteractionSignalMapper
{
    /// <summary>The rating recorded for a positive engagement (a reaction).</summary>
    public const double PositiveRating = 1.0;

    public static InteractionMapping Map(InteractionSignal signal) => signal switch
    {
        // A view, a click into detail, and dwell time are all detail views — dwell
        // additionally carries a duration, which the caller supplies separately.
        InteractionSignal.View or InteractionSignal.Click or InteractionSignal.Dwell
            => new InteractionMapping(OutboxOperation.AddDetailView, RatingValue: null),

        InteractionSignal.Reaction
            => new InteractionMapping(OutboxOperation.AddRating, RatingValue: PositiveRating),

        InteractionSignal.ReactionRemoved
            => new InteractionMapping(OutboxOperation.DeleteRating, RatingValue: null),

        InteractionSignal.Comment or InteractionSignal.Share
            => new InteractionMapping(OutboxOperation.AddBookmark, RatingValue: null),

        _ => throw new ArgumentOutOfRangeException(nameof(signal), signal, "Unmapped interaction signal")
    };
}

/// <summary>Result of mapping a signal: the Recombee operation and, where relevant, its rating.</summary>
public readonly record struct InteractionMapping(OutboxOperation Operation, double? RatingValue);
