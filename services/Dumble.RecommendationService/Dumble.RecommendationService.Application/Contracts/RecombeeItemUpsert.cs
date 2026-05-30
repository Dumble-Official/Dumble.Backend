namespace Dumble.RecommendationService.Application.Contracts;

/// <summary>
/// A partial set of Recombee item properties to upsert. Only the non-null fields are sent,
/// so a create can set everything while an update touches just what changed (e.g. hashtags)
/// without clobbering the rest.
/// </summary>
public sealed record RecombeeItemUpsert(
    string ItemId,
    string? Author = null,
    string? AuthorType = null,
    string? GymId = null,
    IReadOnlyList<string>? Hashtags = null,
    DateTimeOffset? CreatedAt = null);
