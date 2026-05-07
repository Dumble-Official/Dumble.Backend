using MediatR;
using Dumble.SocialService.Application.Common;
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
        var cursor = CursorParsing.ParseUtcCursor(request.Cursor);

        var follows = await _followRepository.GetFollowingAsync(request.UserId, cursor, request.Limit + 1, ct);
        var hasMore = follows.Count > request.Limit;
        if (hasMore) follows = follows.Take(request.Limit).ToList();

        // Followee display info isn't carried on the Follow row (we never see
        // the followee at follow-time), so the response only carries the id
        // and timestamp here. Clients display names by joining against their
        // own user-info cache or a separate user lookup.
        var items = follows.Select(f => new FollowResponse(
            f.FolloweeId, string.Empty, null, f.CreatedAt)).ToList();

        var nextCursor = hasMore && items.Count > 0
            ? CursorParsing.FormatCursor(items.Last().FollowedAt)
            : null;

        return new CursorPagedResponse<FollowResponse>(items, nextCursor, hasMore);
    }
}
