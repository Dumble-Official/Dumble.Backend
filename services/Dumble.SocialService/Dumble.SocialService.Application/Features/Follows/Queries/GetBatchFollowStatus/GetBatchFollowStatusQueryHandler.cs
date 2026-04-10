using MediatR;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Contracts.Follows;

namespace Dumble.SocialService.Application.Features.Follows.Queries.GetBatchFollowStatus;

public class GetBatchFollowStatusQueryHandler : IRequestHandler<GetBatchFollowStatusQuery, BatchFollowStatusResponse>
{
    private readonly IFollowRepository _followRepository;

    public GetBatchFollowStatusQueryHandler(IFollowRepository followRepository)
    {
        _followRepository = followRepository;
    }

    public async Task<BatchFollowStatusResponse> Handle(GetBatchFollowStatusQuery request, CancellationToken ct)
    {
        var statuses = await _followRepository.GetFollowStatusBatchAsync(
            request.FollowerId, request.FolloweeIds, ct);
        return new BatchFollowStatusResponse(statuses);
    }
}
