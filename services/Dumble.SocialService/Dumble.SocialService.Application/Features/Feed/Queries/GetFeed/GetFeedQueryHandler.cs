using MediatR;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Contracts.Common;
using Dumble.SocialService.Contracts.Feed;

namespace Dumble.SocialService.Application.Features.Feed.Queries.GetFeed;

/// <summary>
/// The home feed is now ranked by Recombee in the recommendation service. SocialService keeps the
/// /api/social/feed path for compatibility and simply proxies to GET /api/feed/home, so all feed
/// ranking lives in one engine and social only owns the follow graph.
/// </summary>
public class GetFeedQueryHandler : IRequestHandler<GetFeedQuery, CursorPagedResponse<FeedPostResponse>>
{
    private readonly IHomeFeedClient _homeFeed;

    public GetFeedQueryHandler(IHomeFeedClient homeFeed)
    {
        _homeFeed = homeFeed;
    }

    public Task<CursorPagedResponse<FeedPostResponse>> Handle(GetFeedQuery request, CancellationToken ct)
        => _homeFeed.GetHomeFeedAsync(request.Cursor, request.Limit, ct);
}
