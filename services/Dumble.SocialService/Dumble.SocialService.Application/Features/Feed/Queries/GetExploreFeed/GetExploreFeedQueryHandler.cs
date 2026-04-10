using MediatR;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Contracts.Common;
using Dumble.SocialService.Contracts.Feed;

namespace Dumble.SocialService.Application.Features.Feed.Queries.GetExploreFeed;

public class GetExploreFeedQueryHandler : IRequestHandler<GetExploreFeedQuery, CursorPagedResponse<FeedPostResponse>>
{
    private readonly IFeedCacheService _feedCache;
    private readonly IRankingServiceClient _rankingClient;
    private readonly IPostServiceClient _postClient;

    public GetExploreFeedQueryHandler(
        IFeedCacheService feedCache,
        IRankingServiceClient rankingClient,
        IPostServiceClient postClient)
    {
        _feedCache = feedCache;
        _rankingClient = rankingClient;
        _postClient = postClient;
    }

    public async Task<CursorPagedResponse<FeedPostResponse>> Handle(GetExploreFeedQuery request, CancellationToken ct)
    {
        // Explore feed: trending/popular posts (not personalized)
        var cachedPostIds = await _feedCache.GetFeedAsync("explore", ct);

        if (cachedPostIds is null)
        {
            cachedPostIds = await _rankingClient.RankPostsAsync("explore", [], [], ct);
            if (cachedPostIds.Count > 0)
                await _feedCache.SetFeedAsync("explore", cachedPostIds, ct);
        }

        if (cachedPostIds.Count == 0)
            return new CursorPagedResponse<FeedPostResponse>([], null, false);

        var startIndex = 0;
        if (request.Cursor is not null)
        {
            var cursorIndex = cachedPostIds.IndexOf(request.Cursor);
            startIndex = cursorIndex >= 0 ? cursorIndex + 1 : 0;
        }

        var pageIds = cachedPostIds.Skip(startIndex).Take(request.Limit + 1).ToList();
        var hasMore = pageIds.Count > request.Limit;
        if (hasMore) pageIds = pageIds.Take(request.Limit).ToList();

        if (pageIds.Count == 0)
            return new CursorPagedResponse<FeedPostResponse>([], null, false);

        var posts = await _postClient.GetPostsByIdsAsync(pageIds, ct);
        var postMap = posts.ToDictionary(p => p.Id);
        var ordered = pageIds.Where(id => postMap.ContainsKey(id)).Select(id => postMap[id]).ToList();

        var nextCursor = hasMore && ordered.Count > 0 ? ordered.Last().Id : null;
        return new CursorPagedResponse<FeedPostResponse>(ordered, nextCursor, hasMore);
    }
}
