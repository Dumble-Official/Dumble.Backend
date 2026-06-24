using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Application.Features.Common;
using Dumble.PostService.Contracts.Common;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.Application.Features.Posts.Queries.GetUserLikedPosts;

public class GetUserLikedPostsQueryHandler
    : IRequestHandler<GetUserLikedPostsQuery, CursorPagedResponse<PostResponse>>
{
    private readonly IReactionRepository _reactionRepository;

    public GetUserLikedPostsQueryHandler(IReactionRepository reactionRepository)
    {
        _reactionRepository = reactionRepository;
    }

    public async Task<CursorPagedResponse<PostResponse>> Handle(GetUserLikedPostsQuery request, CancellationToken ct)
    {
        var cursor = CursorParsing.ParseUtcCursor(request.Cursor);

        var rows = await _reactionRepository.GetReactedPostsByUserAsync(request.UserId, cursor, request.Limit + 1, ct);
        var hasMore = rows.Count > request.Limit;
        if (hasMore) rows = rows.Take(request.Limit).ToList();

        var items = rows.Select(row =>
        {
            var post = row.Post;
            return new PostResponse(
                post.Id, post.AuthorId, post.AuthorDisplayName, post.AuthorProfileImage,
                post.AuthorType.ToString(), post.Content, post.GymId, post.Status.ToString(),
                post.ReactionsCount, post.CommentsCount,
                post.Images.Select(i => i.ImageUrl).ToList(),
                post.PostHashtags.Select(ph => ph.Hashtag?.Name ?? string.Empty).Where(n => !string.IsNullOrEmpty(n)).ToList(),
                post.CreatedAt, post.UpdatedAt
            );
        }).ToList();

        // Cursor is the reaction time of the last returned row (reaction recency),
        // not the post CreatedAt, so pagination follows the liked-order.
        var nextCursor = hasMore && rows.Count > 0
            ? CursorParsing.FormatCursor(rows.Last().ReactedAt)
            : null;

        return new CursorPagedResponse<PostResponse>(items, nextCursor, hasMore);
    }
}
