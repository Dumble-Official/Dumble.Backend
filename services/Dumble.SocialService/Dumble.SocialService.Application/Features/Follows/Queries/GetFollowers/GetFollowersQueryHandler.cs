using MediatR;
using Dumble.SocialService.Application.Common;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Contracts.Common;
using Dumble.SocialService.Contracts.Follows;

namespace Dumble.SocialService.Application.Features.Follows.Queries.GetFollowers;

public class GetFollowersQueryHandler : IRequestHandler<GetFollowersQuery, CursorPagedResponse<FollowResponse>>
{
    private readonly IFollowRepository _followRepository;

    public GetFollowersQueryHandler(IFollowRepository followRepository)
    {
        _followRepository = followRepository;
    }

    public async Task<CursorPagedResponse<FollowResponse>> Handle(GetFollowersQuery request, CancellationToken ct)
    {
        var cursor = CursorParsing.ParseUtcCursor(request.Cursor);

        var follows = await _followRepository.GetFollowersAsync(request.UserId, cursor, request.Limit + 1, ct);
        var hasMore = follows.Count > request.Limit;
        if (hasMore) follows = follows.Take(request.Limit).ToList();

        var items = follows.Select(f => new FollowResponse(
            f.FollowerId, f.FollowerName, f.FollowerImage, f.CreatedAt)).ToList();

        var nextCursor = hasMore && items.Count > 0
            ? CursorParsing.FormatCursor(items.Last().FollowedAt)
            : null;

        return new CursorPagedResponse<FollowResponse>(items, nextCursor, hasMore);
    }
}
