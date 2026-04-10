using MediatR;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Contracts.Follows;

namespace Dumble.SocialService.Application.Features.Follows.Queries.GetFollowStatus;

public class GetFollowStatusQueryHandler : IRequestHandler<GetFollowStatusQuery, FollowStatusResponse>
{
    private readonly IFollowRepository _followRepository;

    public GetFollowStatusQueryHandler(IFollowRepository followRepository)
    {
        _followRepository = followRepository;
    }

    public async Task<FollowStatusResponse> Handle(GetFollowStatusQuery request, CancellationToken ct)
    {
        var follow = await _followRepository.GetAsync(request.FollowerId, request.FolloweeId, ct);
        return new FollowStatusResponse(follow is not null);
    }
}
