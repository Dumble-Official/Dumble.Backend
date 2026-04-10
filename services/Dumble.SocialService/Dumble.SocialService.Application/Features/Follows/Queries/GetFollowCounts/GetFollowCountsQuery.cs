using MediatR;
using Dumble.SocialService.Contracts.Follows;

namespace Dumble.SocialService.Application.Features.Follows.Queries.GetFollowCounts;

public record GetFollowCountsQuery(string UserId) : IRequest<FollowCountsResponse>;
