namespace Dumble.RecommendationService.Domain.Outbox;

/// <summary>
/// The exact Recombee write a buffered row represents. Each value maps 1:1 to a
/// Recombee SDK call (design D13): view/click/dwell -> AddDetailView, reaction ->
/// AddRating(+1), un-react -> DeleteRating, comment/share -> AddBookmark.
/// </summary>
public enum OutboxOperation
{
    AddDetailView,
    AddRating,
    AddBookmark,
    DeleteRating
}
