using MediatR;
using Dumble.SocialService.Contracts.Common;
using Dumble.SocialService.Contracts.Feed;

namespace Dumble.SocialService.Application.Features.Feed.Queries.GetFeed;

public record GetFeedQuery(string UserId, string? Cursor, int Limit = 20)
    : IRequest<CursorPagedResponse<FeedPostResponse>>;
