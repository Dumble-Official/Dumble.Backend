using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Application.Features.Common;
using Dumble.PostService.Contracts.Comments;
using Dumble.PostService.Contracts.Common;

namespace Dumble.PostService.Application.Features.Comments.Queries.GetComments;

public class GetCommentsQueryHandler : IRequestHandler<GetCommentsQuery, CursorPagedResponse<CommentResponse>>
{
    private readonly ICommentRepository _commentRepository;

    public GetCommentsQueryHandler(ICommentRepository commentRepository)
    {
        _commentRepository = commentRepository;
    }

    public async Task<CursorPagedResponse<CommentResponse>> Handle(GetCommentsQuery request, CancellationToken ct)
    {
        var cursor = CursorParsing.ParseUtcCursor(request.Cursor);

        var comments = await _commentRepository.GetByPostIdAsync(request.PostId, cursor, request.Limit + 1, ct);
        var hasMore = comments.Count > request.Limit;
        if (hasMore) comments = comments.Take(request.Limit).ToList();

        var commentIds = comments.Select(c => c.Id).ToList();
        var replyCounts = await _commentRepository.GetRepliesCountForManyAsync(commentIds, ct);

        var items = comments.Select(comment => new CommentResponse(
            comment.Id, comment.PostId, comment.AuthorId, comment.AuthorDisplayName,
            comment.AuthorProfileImage, comment.ParentCommentId, comment.Content,
            comment.Status.ToString(), comment.ReactionsCount,
            replyCounts.GetValueOrDefault(comment.Id, 0),
            comment.CreatedAt, comment.UpdatedAt
        )).ToList();

        var nextCursor = hasMore && items.Count > 0
            ? CursorParsing.FormatCursor(items.Last().CreatedAt)
            : null;

        return new CursorPagedResponse<CommentResponse>(items, nextCursor, hasMore);
    }
}
