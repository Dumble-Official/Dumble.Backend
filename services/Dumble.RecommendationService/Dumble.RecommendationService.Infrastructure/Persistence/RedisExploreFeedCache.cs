using Dumble.RecommendationService.Application.Contracts;
using StackExchange.Redis;

namespace Dumble.RecommendationService.Infrastructure.Persistence;

/// <summary>
/// Caches a user's ranked explore snapshot so cursor pages walk a stable list (design D4).
/// Stored as a newline-joined id list with a short TTL; ids never contain newlines.
/// </summary>
public sealed class RedisExploreFeedCache : IExploreFeedCache
{
    private static readonly TimeSpan Ttl = TimeSpan.FromMinutes(10);

    private readonly IDatabase _db;

    public RedisExploreFeedCache(IConnectionMultiplexer redis) => _db = redis.GetDatabase();

    private static string KeyFor(string userId) => $"rec:explore:{userId}";

    public async Task<IReadOnlyList<string>?> GetAsync(string userId, CancellationToken ct = default)
    {
        var value = await _db.StringGetAsync(KeyFor(userId));
        if (value.IsNullOrEmpty)
            return null;

        return value.ToString().Split('\n', StringSplitOptions.RemoveEmptyEntries);
    }

    public Task SetAsync(string userId, IReadOnlyList<string> postIds, CancellationToken ct = default)
        => _db.StringSetAsync(KeyFor(userId), string.Join('\n', postIds), Ttl);
}
