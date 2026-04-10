using MediatR;
using Dumble.SocialService.Contracts.Follows;

namespace Dumble.SocialService.Application.Features.Follows.Queries.GetBatchFollowStatus;

public record GetBatchFollowStatusQuery(string FollowerId, List<string> FolloweeIds)
    : IRequest<BatchFollowStatusResponse>;
