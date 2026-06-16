using Dumble.RecommendationService.Application.Contracts;
using StackExchange.Redis;

namespace Dumble.RecommendationService.Infrastructure.Persistence;

/// <summary>
/// Reads the shared "banned_users" Redis set written by the Auth service's BanService. All services
/// point at the same Redis, so no extra event is needed — a membership check is enough.
/// </summary>
public sealed class RedisBannedUserStore : IBannedUserStore
{
    private const string BannedUsersKey = "banned_users";

    private readonly IDatabase _db;

    public RedisBannedUserStore(IConnectionMultiplexer redis) => _db = redis.GetDatabase();

    public async Task<IReadOnlySet<string>> GetBannedAsync(IReadOnlyCollection<string> userIds, CancellationToken ct = default)
    {
        var distinct = userIds.Distinct().ToList();
        if (distinct.Count == 0)
            return new HashSet<string>();

        var checks = distinct.ToDictionary(id => id, id => _db.SetContainsAsync(BannedUsersKey, id));
        await Task.WhenAll(checks.Values);

        return checks.Where(kv => kv.Value.Result).Select(kv => kv.Key).ToHashSet();
    }
}
