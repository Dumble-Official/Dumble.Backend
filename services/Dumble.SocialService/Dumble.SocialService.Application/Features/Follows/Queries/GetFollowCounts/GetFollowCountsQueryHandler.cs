using MediatR;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Contracts.Follows;

namespace Dumble.SocialService.Application.Features.Follows.Queries.GetFollowCounts;

public class GetFollowCountsQueryHandler : IRequestHandler<GetFollowCountsQuery, FollowCountsResponse>
{
    private readonly IFollowRepository _followRepository;

    public GetFollowCountsQueryHandler(IFollowRepository followRepository)
    {
        _followRepository = followRepository;
    }

    public async Task<FollowCountsResponse> Handle(GetFollowCountsQuery request, CancellationToken ct)
    {
        var followers = await _followRepository.GetFollowersCountAsync(request.UserId, ct);
        var following = await _followRepository.GetFollowingCountAsync(request.UserId, ct);
        return new FollowCountsResponse(followers, following);
    }
}
