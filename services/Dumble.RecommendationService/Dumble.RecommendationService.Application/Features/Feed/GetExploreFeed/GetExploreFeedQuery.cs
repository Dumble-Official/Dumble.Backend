using Dumble.RecommendationService.Contracts.Common;
using Dumble.RecommendationService.Contracts.Feed;
using MediatR;

namespace Dumble.RecommendationService.Application.Features.Feed.GetExploreFeed;

/// <summary>Personalized explore feed for a user, cursor-paginated.</summary>
public sealed record GetExploreFeedQuery(string UserId, string? Cursor, int Limit)
    : IRequest<CursorPagedResponse<FeedPostResponse>>;
