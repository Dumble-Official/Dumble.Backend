namespace Dumble.RecommendationService.Application.Contracts;

/// <summary>
/// A local, eventually-consistent view of who each user follows, maintained from the follow
/// events (design D5). SocialService stays the source of truth; this exists only to exclude
/// already-followed users from suggestions.
/// </summary>
public interface IFollowProjection
{
    Task AddFollowAsync(string followerId, string followeeId, CancellationToken ct = default);
    Task RemoveFollowAsync(string followerId, string followeeId, CancellationToken ct = default);
    Task<IReadOnlyCollection<string>> GetFolloweesAsync(string userId, CancellationToken ct = default);
}
