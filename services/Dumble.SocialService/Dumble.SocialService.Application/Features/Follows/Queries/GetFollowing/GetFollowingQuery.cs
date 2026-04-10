using MediatR;
using Dumble.SocialService.Contracts.Common;
using Dumble.SocialService.Contracts.Follows;

namespace Dumble.SocialService.Application.Features.Follows.Queries.GetFollowing;

public record GetFollowingQuery(string UserId, string? Cursor, int Limit = 20)
    : IRequest<CursorPagedResponse<FollowResponse>>;
