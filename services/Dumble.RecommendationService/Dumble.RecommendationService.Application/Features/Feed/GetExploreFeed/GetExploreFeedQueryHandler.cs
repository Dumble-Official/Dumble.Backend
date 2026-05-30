using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Contracts.Common;
using Dumble.RecommendationService.Contracts.Feed;
using MediatR;
using Microsoft.Extensions.Logging;

namespace Dumble.RecommendationService.Application.Features.Feed.GetExploreFeed;

/// <summary>
/// Serves the explore feed: ranked ids from cache, else Recombee, else a recency fallback
/// (also the cold-start path); then cursor-paginate the cached snapshot and hydrate the page
/// from PostService. Degrades to an empty page rather than erroring when Recombee is down
/// and there is nothing to fall back to (design D8).
/// </summary>
public sealed class GetExploreFeedQueryHandler
    : IRequestHandler<GetExploreFeedQuery, CursorPagedResponse<FeedPostResponse>>
{
    // How many ranked ids to fetch and cache per user; cursor pages walk this snapshot.
    private const int SnapshotSize = 200;

    private readonly IExploreFeedCache _cache;
    private readonly IRecombeeClient _recombee;
    private readonly IRecentPostsStore _recentPosts;
    private readonly IPostHydrator _hydrator;
    private readonly ILogger<GetExploreFeedQueryHandler> _logger;

    public GetExploreFeedQueryHandler(
        IExploreFeedCache cache,
        IRecombeeClient recombee,
        IRecentPostsStore recentPosts,
        IPostHydrator hydrator,
        ILogger<GetExploreFeedQueryHandler> logger)
    {
        _cache = cache;
        _recombee = recombee;
        _recentPosts = recentPosts;
        _hydrator = hydrator;
        _logger = logger;
    }

    public async Task<CursorPagedResponse<FeedPostResponse>> Handle(
        GetExploreFeedQuery request, CancellationToken ct)
    {
        var postIds = await _cache.GetAsync(request.UserId, ct);
        if (postIds is null)
        {
            postIds = await BuildRankedSnapshotAsync(request.UserId, ct);
            if (postIds.Count > 0)
                await _cache.SetAsync(request.UserId, postIds, ct);
        }

        if (postIds.Count == 0)
            return Empty();

        // Cursor pagination over the stable snapshot (cursor = last post id of the prior page).
        var startIndex = 0;
        if (request.Cursor is not null)
        {
            var cursorIndex = postIds.ToList().IndexOf(request.Cursor);
            startIndex = cursorIndex >= 0 ? cursorIndex + 1 : 0;
        }

        var pageIds = postIds.Skip(startIndex).Take(request.Limit + 1).ToList();
        var hasMore = pageIds.Count > request.Limit;
        if (hasMore)
            pageIds = pageIds.Take(request.Limit).ToList();

        if (pageIds.Count == 0)
            return Empty();

        // Hydrate from PostService; drop any ids it can't return (e.g. deleted), preserving order.
        var posts = await _hydrator.HydrateAsync(pageIds, ct);
        var byId = posts.ToDictionary(p => p.Id);
        var ordered = pageIds.Where(byId.ContainsKey).Select(id => byId[id]).ToList();

        var nextCursor = hasMore && ordered.Count > 0 ? ordered[^1].Id : null;
        return new CursorPagedResponse<FeedPostResponse>(ordered, nextCursor, hasMore);
    }

    private async Task<IReadOnlyList<string>> BuildRankedSnapshotAsync(string userId, CancellationToken ct)
    {
        IReadOnlyList<string> ranked;
        try
        {
            ranked = await _recombee.RecommendItemsToUserAsync(userId, SnapshotSize, ct);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex,
                "Recombee explore recommendation failed for {UserId}; falling back to recency", userId);
            ranked = Array.Empty<string>();
        }

        // Empty means Recombee is unreachable or the user has too little history (cold start).
        if (ranked.Count == 0)
            ranked = await _recentPosts.GetRecentAsync(SnapshotSize, ct);

        return ranked;
    }

    private static CursorPagedResponse<FeedPostResponse> Empty()
        => new(new List<FeedPostResponse>(), null, false);
}
