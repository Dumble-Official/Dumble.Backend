using Dumble.RecommendationService.Application.Contracts;
using StackExchange.Redis;

namespace Dumble.RecommendationService.Infrastructure.Persistence;

/// <summary>
/// Recent posts as a Redis sorted set scored by creation time, bounded to the newest
/// <see cref="MaxSize"/>. Derived from the post catalog events (design D8); rebuildable, so
/// it lives in Redis rather than the durable store.
/// </summary>
public sealed class RedisRecentPostsStore : IRecentPostsStore
{
    private const string Key = "rec:recent_posts";
    private const int MaxSize = 1000;

    private readonly IDatabase _db;

    public RedisRecentPostsStore(IConnectionMultiplexer redis) => _db = redis.GetDatabase();

    public async Task AddAsync(string postId, DateTimeOffset createdAt, CancellationToken ct = default)
    {
        await _db.SortedSetAddAsync(Key, postId, createdAt.ToUnixTimeMilliseconds());
        // Keep only the newest MaxSize: drop everything below the top MaxSize by rank.
        await _db.SortedSetRemoveRangeByRankAsync(Key, 0, -(MaxSize + 1));
    }

    public Task RemoveAsync(string postId, CancellationToken ct = default)
        => _db.SortedSetRemoveAsync(Key, postId);

    public async Task<IReadOnlyList<string>> GetRecentAsync(int count, CancellationToken ct = default)
    {
        var values = await _db.SortedSetRangeByRankAsync(Key, 0, count - 1, Order.Descending);
        return values.Select(v => v.ToString()).ToList();
    }
}
