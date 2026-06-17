namespace Dumble.RecommendationService.Domain.Outbox;

/// <summary>
/// A behaviour signal as the rest of the app expresses it, before it is mapped to a
/// concrete Recombee operation. Client-only signals (View/Click/Dwell) arrive over
/// HTTP; the rest are sourced from existing domain events (reaction/comment/un-react)
/// per the two-channel ingestion design.
/// </summary>
/// <remarks>
/// A "Share" signal is intentionally absent: the platform has no share action, so nothing
/// could ever produce it. To re-add when sharing ships: add a PostSharedEvent, publish it
/// from the share endpoint, map it here (e.g. AddBookmark) and add a PostSharedConsumer.
/// </remarks>
public enum InteractionSignal
{
    View,
    Click,
    Dwell,
    Reaction,
    ReactionRemoved,
    Comment,
    CommentRemoved
}
