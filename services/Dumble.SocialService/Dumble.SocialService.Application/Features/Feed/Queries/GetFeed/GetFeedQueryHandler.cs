using MediatR;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Contracts.Common;
using Dumble.SocialService.Contracts.Feed;

namespace Dumble.SocialService.Application.Features.Feed.Queries.GetFeed;

public class GetFeedQueryHandler : IRequestHandler<GetFeedQuery, CursorPagedResponse<FeedPostResponse>>
{
    private readonly IFollowRepository _followRepository;
    private readonly IUserBehaviorRepository _behaviorRepository;
    private readonly IFeedCacheService _feedCache;
    private readonly IRankingServiceClient _rankingClient;
    private readonly IPostServiceClient _postClient;

    public GetFeedQueryHandler(
        IFollowRepository followRepository,
        IUserBehaviorRepository behaviorRepository,
        IFeedCacheService feedCache,
        IRankingServiceClient rankingClient,
        IPostServiceClient postClient)
    {
        _followRepository = followRepository;
        _behaviorRepository = behaviorRepository;
        _feedCache = feedCache;
        _rankingClient = rankingClient;
        _postClient = postClient;
    }

    public async Task<CursorPagedResponse<FeedPostResponse>> Handle(GetFeedQuery request, CancellationToken ct)
    {
        // Step 1: Check cache
        var cachedPostIds = await _feedCache.GetFeedAsync(request.UserId, ct);

        if (cachedPostIds is null)
        {
            // Step 2: Get followed users
            var followedUserIds = await _followRepository.GetFolloweeIdsAsync(request.UserId, ct);

            if (followedUserIds.Count == 0)
                return new CursorPagedResponse<FeedPostResponse>([], null, false);

            // Step 3: Call AI ranking
            var recentBehavior = await _behaviorRepository.GetRecentAsync(request.UserId, 100, ct);
            cachedPostIds = await _rankingClient.RankPostsAsync(
                request.UserId, followedUserIds, recentBehavior, ct);

            // Cache the ranked list
            if (cachedPostIds.Count > 0)
                await _feedCache.SetFeedAsync(request.UserId, cachedPostIds, ct);
        }

        if (cachedPostIds.Count == 0)
            return new CursorPagedResponse<FeedPostResponse>([], null, false);

        // Step 4: Cursor-based pagination over the cached list
        var startIndex = 0;
        if (request.Cursor is not null)
        {
            var cursorIndex = cachedPostIds.IndexOf(request.Cursor);
            startIndex = cursorIndex >= 0 ? cursorIndex + 1 : 0;
        }

        var pageIds = cachedPostIds
            .Skip(startIndex)
            .Take(request.Limit + 1)
            .ToList();

        var hasMore = pageIds.Count > request.Limit;
        if (hasMore) pageIds = pageIds.Take(request.Limit).ToList();

        if (pageIds.Count == 0)
            return new CursorPagedResponse<FeedPostResponse>([], null, false);

        // Step 5: Hydrate from PostService
        var posts = await _postClient.GetPostsByIdsAsync(pageIds, ct);

        // Preserve ranking order
        var postMap = posts.ToDictionary(p => p.Id);
        var ordered = pageIds
            .Where(id => postMap.ContainsKey(id))
            .Select(id => postMap[id])
            .ToList();

        var nextCursor = hasMore && ordered.Count > 0 ? ordered.Last().Id : null;

        return new CursorPagedResponse<FeedPostResponse>(ordered, nextCursor, hasMore);
    }
}
