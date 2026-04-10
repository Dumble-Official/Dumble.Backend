using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Comments;
using Dumble.PostService.Contracts.Common;

namespace Dumble.PostService.Application.Features.Comments.Queries.GetReplies;

public class GetRepliesQueryHandler : IRequestHandler<GetRepliesQuery, CursorPagedResponse<CommentResponse>>
{
    private readonly ICommentRepository _commentRepository;

    public GetRepliesQueryHandler(ICommentRepository commentRepository)
    {
        _commentRepository = commentRepository;
    }

    public async Task<CursorPagedResponse<CommentResponse>> Handle(GetRepliesQuery request, CancellationToken ct)
    {
        DateTime? cursor = request.Cursor is not null ? DateTime.Parse(request.Cursor) : null;

        var replies = await _commentRepository.GetRepliesAsync(request.ParentCommentId, cursor, request.Limit + 1, ct);
        var hasMore = replies.Count > request.Limit;
        if (hasMore) replies = replies.Take(request.Limit).ToList();

        var items = new List<CommentResponse>();
        foreach (var reply in replies)
        {
            var repliesCount = await _commentRepository.GetRepliesCountAsync(reply.Id, ct);
            items.Add(new CommentResponse(
                reply.Id, reply.PostId, reply.AuthorId, reply.AuthorDisplayName,
                reply.AuthorProfileImage, reply.ParentCommentId, reply.Content,
                reply.Status.ToString(), reply.ReactionsCount, repliesCount,
                reply.CreatedAt, reply.UpdatedAt
            ));
        }

        var nextCursor = hasMore && items.Count > 0
            ? items.Last().CreatedAt.ToString("O")
            : null;

        return new CursorPagedResponse<CommentResponse>(items, nextCursor, hasMore);
    }
}
