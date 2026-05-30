namespace Dumble.RecommendationService.Application.Contracts;

/// <summary>
/// Reads PostService's catalog one page at a time (the source of truth for the item set),
/// already shaped as Recombee upserts. The cursor is opaque — pass back what the previous
/// page returned; a null next cursor means the sweep is done.
/// </summary>
public interface IPostCatalogSource
{
    Task<PostCatalogPage> GetPageAsync(string? cursor, int limit, CancellationToken ct = default);

    /// <summary>
    /// Of the given ids, returns those that still exist as live (non-deleted) posts. Lets the
    /// orphan sweep confirm a candidate against the source of truth before deleting it, so a post
    /// the catalog cursor merely skipped is never mistaken for a deleted one.
    /// </summary>
    Task<IReadOnlySet<string>> GetExistingIdsAsync(IReadOnlyList<string> ids, CancellationToken ct = default);
}

public sealed record PostCatalogPage(IReadOnlyList<RecombeeItemUpsert> Items, string? NextCursor);
