namespace Dumble.SocialService.Application.Contracts;

public interface IFeedCacheService
{
    Task<List<string>?> GetFeedAsync(string userId, CancellationToken ct = default);
    Task SetFeedAsync(string userId, List<string> postIds, CancellationToken ct = default);
    Task InvalidateFeedAsync(string userId, CancellationToken ct = default);
    Task InvalidateFeedsForFollowersAsync(string authorId, List<string> followerIds, CancellationToken ct = default);
}
