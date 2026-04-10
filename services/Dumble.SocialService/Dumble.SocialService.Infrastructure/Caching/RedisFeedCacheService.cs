using System.Text.Json;
using StackExchange.Redis;
using Dumble.SocialService.Application.Contracts;

namespace Dumble.SocialService.Infrastructure.Caching;

public class RedisFeedCacheService : IFeedCacheService
{
    private readonly IConnectionMultiplexer _redis;
    private static readonly TimeSpan CacheTtl = TimeSpan.FromMinutes(5);

    public RedisFeedCacheService(IConnectionMultiplexer redis)
    {
        _redis = redis;
    }

    public async Task<List<string>?> GetFeedAsync(string userId, CancellationToken ct = default)
    {
        var db = _redis.GetDatabase();
        var value = await db.StringGetAsync($"feed:{userId}");
        if (value.IsNullOrEmpty) return null;
        return JsonSerializer.Deserialize<List<string>>(value!);
    }

    public async Task SetFeedAsync(string userId, List<string> postIds, CancellationToken ct = default)
    {
        var db = _redis.GetDatabase();
        var json = JsonSerializer.Serialize(postIds);
        await db.StringSetAsync($"feed:{userId}", json, CacheTtl);
    }

    public async Task InvalidateFeedAsync(string userId, CancellationToken ct = default)
    {
        var db = _redis.GetDatabase();
        await db.KeyDeleteAsync($"feed:{userId}");
    }

    public async Task InvalidateFeedsForFollowersAsync(string authorId, List<string> followerIds, CancellationToken ct = default)
    {
        var db = _redis.GetDatabase();
        var keys = followerIds.Select(id => (RedisKey)$"feed:{id}").ToArray();
        if (keys.Length > 0)
            await db.KeyDeleteAsync(keys);
    }
}
