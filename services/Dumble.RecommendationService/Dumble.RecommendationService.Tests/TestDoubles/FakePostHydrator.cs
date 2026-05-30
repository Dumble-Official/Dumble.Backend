using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Contracts.Feed;

namespace Dumble.RecommendationService.Tests.TestDoubles;

internal sealed class FakePostHydrator : IPostHydrator
{
    /// <summary>Ids the hydrator should pretend it can't return (e.g. deleted in PostService).</summary>
    public HashSet<string> Missing { get; } = new();

    public Task<IReadOnlyList<FeedPostResponse>> HydrateAsync(IReadOnlyList<string> postIds, CancellationToken ct = default)
    {
        var posts = postIds
            .Where(id => !Missing.Contains(id))
            .Select(id => new FeedPostResponse(
                id, "author", "Author", null, "content", new List<string>(), 0, 0, DateTime.UtcNow))
            .ToList();
        return Task.FromResult<IReadOnlyList<FeedPostResponse>>(posts);
    }
}
