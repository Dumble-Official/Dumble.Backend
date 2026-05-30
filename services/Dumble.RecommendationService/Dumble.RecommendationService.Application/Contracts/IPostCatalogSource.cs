namespace Dumble.RecommendationService.Application.Contracts;

/// <summary>
/// Reads PostService's catalog one page at a time (the source of truth for the item set),
/// already shaped as Recombee upserts. The cursor is opaque — pass back what the previous
/// page returned; a null next cursor means the sweep is done.
/// </summary>
public interface IPostCatalogSource
{
    Task<PostCatalogPage> GetPageAsync(string? cursor, int limit, CancellationToken ct = default);
}

public sealed record PostCatalogPage(IReadOnlyList<RecombeeItemUpsert> Items, string? NextCursor);
