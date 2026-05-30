using Dumble.RecommendationService.Application.Contracts;

namespace Dumble.RecommendationService.Tests.TestDoubles;

internal sealed class FakeFollowProjection : IFollowProjection
{
    private readonly Dictionary<string, HashSet<string>> _follows = new();

    public void Seed(string userId, params string[] followeeIds) => _follows[userId] = followeeIds.ToHashSet();

    public Task AddFollowAsync(string followerId, string followeeId, CancellationToken ct = default)
    {
        (_follows.TryGetValue(followerId, out var set) ? set : _follows[followerId] = new HashSet<string>()).Add(followeeId);
        return Task.CompletedTask;
    }

    public Task RemoveFollowAsync(string followerId, string followeeId, CancellationToken ct = default)
    {
        if (_follows.TryGetValue(followerId, out var set)) set.Remove(followeeId);
        return Task.CompletedTask;
    }

    public Task RemoveUserAsync(string userId, CancellationToken ct = default)
    {
        _follows.Remove(userId);
        return Task.CompletedTask;
    }

    public bool Contains(string userId) => _follows.ContainsKey(userId);

    public Task<IReadOnlyCollection<string>> GetFolloweesAsync(string userId, CancellationToken ct = default)
        => Task.FromResult<IReadOnlyCollection<string>>(
            _follows.TryGetValue(userId, out var set) ? set : new HashSet<string>());
}
