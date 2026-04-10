using MediatR;
using Dumble.SocialService.Contracts.Common;
using Dumble.SocialService.Contracts.Feed;

namespace Dumble.SocialService.Application.Features.Feed.Queries.GetExploreFeed;

public record GetExploreFeedQuery(string? Cursor, int Limit = 20)
    : IRequest<CursorPagedResponse<FeedPostResponse>>;
