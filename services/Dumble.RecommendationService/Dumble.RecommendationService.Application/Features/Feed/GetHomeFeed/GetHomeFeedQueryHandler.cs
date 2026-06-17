using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Contracts.Common;
using Dumble.RecommendationService.Contracts.Feed;
using MediatR;
using Microsoft.Extensions.Logging;

namespace Dumble.RecommendationService.Application.Features.Feed.GetHomeFeed;

/// <summary>
/// Serves the home feed: posts from the people the caller follows, ranked by the same Recombee
/// per-user model that powers explore — just filtered to the followee set. No follows -> empty.
/// Recombee handles cold start (a new follower still gets popular posts among their followees);
/// a Recombee outage degrades to an empty page rather than erroring.
/// </summary>
public sealed class GetHomeFeedQueryHandler
    : IRequestHandler<GetHomeFeedQuery, CursorPagedResponse<FeedPostResponse>>
{
    // Ranked ids fetched per request; cursor pages walk this snapshot.
    private const int SnapshotSize = 200;

    private readonly IFollowProjection _follows;
    private readonly IRecombeeClient _recombee;
    private readonly IPostHydrator _hydrator;
    private readonly ILogger<GetHomeFeedQueryHandler> _logger;

    public GetHomeFeedQueryHandler(
        IFollowProjection follows,
        IRecombeeClient recombee,
        IPostHydrator hydrator,
        ILogger<GetHomeFeedQueryHandler> logger)
    {
        _follows = follows;
        _recombee = recombee;
        _hydrator = hydrator;
        _logger = logger;
    }

    public async Task<CursorPagedResponse<FeedPostResponse>> Handle(
        GetHomeFeedQuery request, CancellationToken ct)
    {
        var followees = await _follows.GetFolloweesAsync(request.UserId, ct);
        if (followees.Count == 0)
            return Empty();

        IReadOnlyList<string> ranked;
        try
        {
            ranked = await _recombee.RecommendFollowedItemsAsync(request.UserId, SnapshotSize, followees, ct);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Recombee home recommendation failed for {UserId}", request.UserId);
            return Empty();
        }

        if (ranked.Count == 0)
            return Empty();

        // Cursor pagination over the ranked snapshot (cursor = last post id of the prior page).
        var startIndex = 0;
        if (request.Cursor is not null)
        {
            var cursorIndex = ranked.ToList().IndexOf(request.Cursor);
            startIndex = cursorIndex >= 0 ? cursorIndex + 1 : 0;
        }

        var pageIds = ranked.Skip(startIndex).Take(request.Limit + 1).ToList();
        var hasMore = pageIds.Count > request.Limit;
        if (hasMore)
            pageIds = pageIds.Take(request.Limit).ToList();

        if (pageIds.Count == 0)
            return Empty();

        // Hydrate from PostService; drop ids it can't return (e.g. deleted), preserving order.
        var posts = await _hydrator.HydrateAsync(pageIds, ct);
        var byId = posts.ToDictionary(p => p.Id);
        var ordered = pageIds.Where(byId.ContainsKey).Select(id => byId[id]).ToList();

        var nextCursor = hasMore && ordered.Count > 0 ? ordered[^1].Id : null;
        return new CursorPagedResponse<FeedPostResponse>(ordered, nextCursor, hasMore);
    }

    private static CursorPagedResponse<FeedPostResponse> Empty()
        => new(new List<FeedPostResponse>(), null, false);
}
