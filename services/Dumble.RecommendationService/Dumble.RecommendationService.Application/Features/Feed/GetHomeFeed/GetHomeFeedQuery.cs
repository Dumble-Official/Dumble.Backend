using Dumble.RecommendationService.Contracts.Common;
using Dumble.RecommendationService.Contracts.Feed;
using MediatR;

namespace Dumble.RecommendationService.Application.Features.Feed.GetHomeFeed;

/// <summary>The home feed: Recombee-ranked posts from the people the caller follows, cursor-paginated.</summary>
public sealed record GetHomeFeedQuery(string UserId, string? Cursor, int Limit)
    : IRequest<CursorPagedResponse<FeedPostResponse>>;
