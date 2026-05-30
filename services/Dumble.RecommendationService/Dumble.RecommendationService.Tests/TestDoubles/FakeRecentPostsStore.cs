using Dumble.RecommendationService.Application.Contracts;

namespace Dumble.RecommendationService.Tests.TestDoubles;

internal sealed class FakeRecentPostsStore : IRecentPostsStore
{
    public List<string> Recent { get; set; } = new();

    public Task AddAsync(string postId, DateTimeOffset createdAt, CancellationToken ct = default)
    {
        Recent.Insert(0, postId);
        return Task.CompletedTask;
    }

    public Task RemoveAsync(string postId, CancellationToken ct = default)
    {
        Recent.Remove(postId);
        return Task.CompletedTask;
    }

    public Task<IReadOnlyList<string>> GetRecentAsync(int count, CancellationToken ct = default)
        => Task.FromResult<IReadOnlyList<string>>(Recent.Take(count).ToList());
}
