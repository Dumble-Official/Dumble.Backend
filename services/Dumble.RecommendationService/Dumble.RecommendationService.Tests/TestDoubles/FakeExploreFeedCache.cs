using Dumble.RecommendationService.Application.Contracts;

namespace Dumble.RecommendationService.Tests.TestDoubles;

internal sealed class FakeExploreFeedCache : IExploreFeedCache
{
    private readonly Dictionary<string, IReadOnlyList<string>> _store = new();
    public int SetCalls { get; private set; }

    public void Seed(string userId, params string[] ids) => _store[userId] = ids;

    public Task<IReadOnlyList<string>?> GetAsync(string userId, CancellationToken ct = default)
        => Task.FromResult(_store.TryGetValue(userId, out var v) ? v : null);

    public Task SetAsync(string userId, IReadOnlyList<string> postIds, CancellationToken ct = default)
    {
        _store[userId] = postIds;
        SetCalls++;
        return Task.CompletedTask;
    }
}
