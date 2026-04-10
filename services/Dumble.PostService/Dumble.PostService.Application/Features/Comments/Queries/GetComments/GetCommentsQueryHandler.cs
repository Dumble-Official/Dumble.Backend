using MediatR;
using Dumble.PostService.Application.Contracts;
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
        DateTime? cursor = request.Cursor is not null ? DateTime.Parse(request.Cursor) : null;

        var comments = await _commentRepository.GetByPostIdAsync(request.PostId, cursor, request.Limit + 1, ct);
        var hasMore = comments.Count > request.Limit;
        if (hasMore) comments = comments.Take(request.Limit).ToList();

        var items = new List<CommentResponse>();
        foreach (var comment in comments)
        {
            var repliesCount = await _commentRepository.GetRepliesCountAsync(comment.Id, ct);
            items.Add(new CommentResponse(
                comment.Id, comment.PostId, comment.AuthorId, comment.AuthorDisplayName,
                comment.AuthorProfileImage, comment.ParentCommentId, comment.Content,
                comment.Status.ToString(), comment.ReactionsCount, repliesCount,
                comment.CreatedAt, comment.UpdatedAt
            ));
        }

        var nextCursor = hasMore && items.Count > 0
            ? items.Last().CreatedAt.ToString("O")
            : null;

        return new CursorPagedResponse<CommentResponse>(items, nextCursor, hasMore);
    }
}
