using Dumble.RecommendationService.Application.Contracts;
using StackExchange.Redis;

namespace Dumble.RecommendationService.Infrastructure.Persistence;

/// <summary>
/// Who each user follows, as a Redis set per follower. Derived from follow events (design D5),
/// used only to exclude already-followed users from suggestions; SocialService is the source
/// of truth.
/// </summary>
public sealed class RedisFollowProjection : IFollowProjection
{
    private readonly IDatabase _db;

    public RedisFollowProjection(IConnectionMultiplexer redis) => _db = redis.GetDatabase();

    private static string KeyFor(string userId) => $"rec:follows:{userId}";

    public Task AddFollowAsync(string followerId, string followeeId, CancellationToken ct = default)
        => _db.SetAddAsync(KeyFor(followerId), followeeId);

    public Task RemoveFollowAsync(string followerId, string followeeId, CancellationToken ct = default)
        => _db.SetRemoveAsync(KeyFor(followerId), followeeId);

    public async Task<IReadOnlyCollection<string>> GetFolloweesAsync(string userId, CancellationToken ct = default)
    {
        var members = await _db.SetMembersAsync(KeyFor(userId));
        // HashSet so the handler's Contains check is O(1).
        return members.Select(m => m.ToString()).ToHashSet();
    }
}
