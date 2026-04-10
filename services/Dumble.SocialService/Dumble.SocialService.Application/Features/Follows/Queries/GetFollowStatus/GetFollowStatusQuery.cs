using MediatR;
using Dumble.SocialService.Contracts.Follows;

namespace Dumble.SocialService.Application.Features.Follows.Queries.GetFollowStatus;

public record GetFollowStatusQuery(string FollowerId, string FolloweeId) : IRequest<FollowStatusResponse>;
