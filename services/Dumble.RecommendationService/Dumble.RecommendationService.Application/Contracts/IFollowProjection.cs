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

    /// <summary>
    /// Forget a deleted user's own follow set — right-to-be-forgotten. Stale entries in other
    /// users' sets are harmless (a deleted user has no profile/interactions, so is never
    /// suggested) and there is no reverse index to find them cheaply.
    /// </summary>
    Task RemoveUserAsync(string userId, CancellationToken ct = default);
}
