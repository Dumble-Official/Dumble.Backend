namespace Dumble.RecommendationService.Domain.Outbox;

/// <summary>
/// A behaviour signal as the rest of the app expresses it, before it is mapped to a
/// concrete Recombee operation. Client-only signals (View/Click/Dwell) arrive over
/// HTTP; the rest are sourced from existing domain events (reaction/comment/share/
/// un-react) per the two-channel ingestion design.
/// </summary>
public enum InteractionSignal
{
    View,
    Click,
    Dwell,
    Reaction,
    ReactionRemoved,
    Comment,
    CommentRemoved,
    Share
}
