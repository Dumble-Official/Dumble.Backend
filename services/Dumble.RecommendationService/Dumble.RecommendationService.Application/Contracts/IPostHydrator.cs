using Dumble.RecommendationService.Contracts.Feed;

namespace Dumble.RecommendationService.Application.Contracts;

/// <summary>
/// Turns ranked post ids into renderable posts by fetching them from PostService (the system
/// of record for content). Recombee only returns ids, so hydration is always required.
/// </summary>
public interface IPostHydrator
{
    Task<IReadOnlyList<FeedPostResponse>> HydrateAsync(IReadOnlyList<string> postIds, CancellationToken ct = default);
}
