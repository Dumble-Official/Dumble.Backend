using MediatR;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Contracts.Common;
using Dumble.SocialService.Contracts.Follows;

namespace Dumble.SocialService.Application.Features.Follows.Queries.GetFollowing;

public class GetFollowingQueryHandler : IRequestHandler<GetFollowingQuery, CursorPagedResponse<FollowResponse>>
{
    private readonly IFollowRepository _followRepository;

    public GetFollowingQueryHandler(IFollowRepository followRepository)
    {
        _followRepository = followRepository;
    }

    public async Task<CursorPagedResponse<FollowResponse>> Handle(GetFollowingQuery request, CancellationToken ct)
    {
        DateTime? cursor = request.Cursor is not null
            ? DateTime.Parse(request.Cursor)
            : null;

        var follows = await _followRepository.GetFollowingAsync(request.UserId, cursor, request.Limit + 1, ct);
        var hasMore = follows.Count > request.Limit;
        if (hasMore) follows = follows.Take(request.Limit).ToList();

        var items = follows.Select(f => new FollowResponse(
            f.FolloweeId, f.FolloweeId, null, f.CreatedAt)).ToList();

        var nextCursor = hasMore && items.Count > 0
            ? items.Last().FollowedAt.ToString("O")
            : null;

        return new CursorPagedResponse<FollowResponse>(items, nextCursor, hasMore);
    }
}
