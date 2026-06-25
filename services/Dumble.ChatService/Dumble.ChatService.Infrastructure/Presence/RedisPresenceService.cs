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

    // Presence is a Redis SET of the user's live connection ids, so a user with
    // multiple connections (multi-device, or a reconnect that overlaps the old
    // socket) stays online until the LAST one goes away. The whole key carries a
    // 60s TTL refreshed on every connect/heartbeat, so abruptly-dropped
    // connections self-expire instead of pinning the user online forever.
    public async Task<bool> SetOnlineAsync(string userId, string connectionId, CancellationToken ct = default)
    {
        var db = _redis.GetDatabase();
        var key = (RedisKey)$"presence:{userId}";
        var added = await db.SetAddAsync(key, connectionId);
        await db.KeyExpireAsync(key, PresenceTtl);
        // First live connection => offline→online transition.
        return added && await db.SetLengthAsync(key) == 1;
    }

    public async Task<bool> SetOfflineAsync(string userId, string connectionId, CancellationToken ct = default)
    {
        var db = _redis.GetDatabase();
        var key = (RedisKey)$"presence:{userId}";
        await db.SetRemoveAsync(key, connectionId);
        if (await db.SetLengthAsync(key) == 0)
        {
            await db.KeyDeleteAsync(key);
            return true; // last connection gone => online→offline transition
        }
        // Other connections remain — keep the user online and refresh the TTL.
        await db.KeyExpireAsync(key, PresenceTtl);
        return false;
    }

    public async Task<bool> IsOnlineAsync(string userId, CancellationToken ct = default)
    {
        var db = _redis.GetDatabase();
        return await db.KeyExistsAsync($"presence:{userId}");
    }

    public async Task<Dictionary<string, bool>> GetBatchOnlineStatusAsync(List<string> userIds, CancellationToken ct = default)
    {
        var db = _redis.GetDatabase();
        // Key existence == has at least one live connection. (StringGet can't be
        // used here anymore: the value is a SET, which would WRONGTYPE.)
        var checks = userIds
            .Select(id => (id, task: db.KeyExistsAsync($"presence:{id}")))
            .ToList();
        await Task.WhenAll(checks.Select(c => c.task));
        return checks.ToDictionary(c => c.id, c => c.task.Result);
    }

    public async Task SetTypingAsync(string conversationId, string userId, CancellationToken ct = default)
    {
        var db = _redis.GetDatabase();
        await db.StringSetAsync($"typing:{conversationId}:{userId}", "1", TypingTtl);
    }
}
