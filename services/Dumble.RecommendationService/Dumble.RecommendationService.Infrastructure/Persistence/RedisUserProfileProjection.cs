using Dumble.RecommendationService.Application.Contracts;
using StackExchange.Redis;

namespace Dumble.RecommendationService.Infrastructure.Persistence;

/// <summary>
/// User display name + avatar as a Redis hash per user, harvested from actor fields on the
/// events the service consumes (design D7). Used to hydrate suggestion cards; absence simply
/// means that user is omitted from suggestions.
/// </summary>
public sealed class RedisUserProfileProjection : IUserProfileProjection
{
    private const string NameField = "name";
    private const string ImageField = "image";

    private readonly IDatabase _db;

    public RedisUserProfileProjection(IConnectionMultiplexer redis) => _db = redis.GetDatabase();

    private static string KeyFor(string userId) => $"rec:profile:{userId}";

    public Task SetAsync(string userId, string displayName, string? profileImage, CancellationToken ct = default)
        => _db.HashSetAsync(KeyFor(userId), new[]
        {
            new HashEntry(NameField, displayName),
            new HashEntry(ImageField, profileImage ?? string.Empty)
        });

    public Task RemoveAsync(string userId, CancellationToken ct = default)
        => _db.KeyDeleteAsync(KeyFor(userId));

    public async Task<IReadOnlyDictionary<string, UserProfile>> GetManyAsync(
        IReadOnlyCollection<string> userIds, CancellationToken ct = default)
    {
        var lookups = userIds.Distinct().ToDictionary(id => id, id => _db.HashGetAllAsync(KeyFor(id)));
        await Task.WhenAll(lookups.Values);

        var result = new Dictionary<string, UserProfile>();
        foreach (var (id, task) in lookups)
        {
            var entries = task.Result;
            if (entries.Length == 0)
                continue;

            var fields = entries.ToDictionary(e => e.Name.ToString(), e => e.Value);
            var name = fields.TryGetValue(NameField, out var n) ? n.ToString() : null;
            if (string.IsNullOrEmpty(name))
                continue;

            var image = fields.TryGetValue(ImageField, out var img) && !img.IsNullOrEmpty ? img.ToString() : null;
            result[id] = new UserProfile(name, image);
        }

        return result;
    }
}
