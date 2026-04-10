using StackExchange.Redis;
using Dumble.ChatService.Application.Contracts;

namespace Dumble.ChatService.Infrastructure.Presence;

public class RedisPresenceService : IPresenceService
{
    private readonly IConnectionMultiplexer _redis;
    private static readonly TimeSpan PresenceTtl = TimeSpan.FromSeconds(60);
    private static readonly TimeSpan TypingTtl = TimeSpan.FromSeconds(3);

    public RedisPresenceService(IConnectionMultiplexer redis)
    {
        _redis = redis;
    }

    public async Task SetOnlineAsync(string userId, CancellationToken ct = default)
    {
        var db = _redis.GetDatabase();
        await db.StringSetAsync($"presence:{userId}", "online", PresenceTtl);
    }

    public async Task SetOfflineAsync(string userId, CancellationToken ct = default)
    {
        var db = _redis.GetDatabase();
        await db.KeyDeleteAsync($"presence:{userId}");
    }

    public async Task<bool> IsOnlineAsync(string userId, CancellationToken ct = default)
    {
        var db = _redis.GetDatabase();
        return await db.KeyExistsAsync($"presence:{userId}");
    }

    public async Task<Dictionary<string, bool>> GetBatchOnlineStatusAsync(List<string> userIds, CancellationToken ct = default)
    {
        var db = _redis.GetDatabase();
        var keys = userIds.Select(id => (RedisKey)$"presence:{id}").ToArray();
        var values = await db.StringGetAsync(keys);

        return userIds.Select((id, i) => new { id, online = !values[i].IsNullOrEmpty })
            .ToDictionary(x => x.id, x => x.online);
    }

    public async Task SetTypingAsync(string conversationId, string userId, CancellationToken ct = default)
    {
        var db = _redis.GetDatabase();
        await db.StringSetAsync($"typing:{conversationId}:{userId}", "1", TypingTtl);
    }
}
