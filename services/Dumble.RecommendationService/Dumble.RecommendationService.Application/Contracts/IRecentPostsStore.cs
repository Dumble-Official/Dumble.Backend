namespace Dumble.RecommendationService.Application.Contracts;

/// <summary>
/// A bounded, recency-ordered index of post ids (design D8). Feeds the explore recency
/// fallback and cold-start path so a user with no personalization still sees recent content.
/// </summary>
public interface IRecentPostsStore
{
    Task AddAsync(string postId, DateTimeOffset createdAt, CancellationToken ct = default);
    Task RemoveAsync(string postId, CancellationToken ct = default);
    Task<IReadOnlyList<string>> GetRecentAsync(int count, CancellationToken ct = default);
}
