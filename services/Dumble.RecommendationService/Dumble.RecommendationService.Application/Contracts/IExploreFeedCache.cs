namespace Dumble.RecommendationService.Application.Contracts;

/// <summary>
/// Caches a user's ranked explore post-id list so cursor pages are served from a stable
/// snapshot (design D4) rather than re-querying Recombee on every page. Null means a miss.
/// </summary>
public interface IExploreFeedCache
{
    Task<IReadOnlyList<string>?> GetAsync(string userId, CancellationToken ct = default);
    Task SetAsync(string userId, IReadOnlyList<string> postIds, CancellationToken ct = default);
}
