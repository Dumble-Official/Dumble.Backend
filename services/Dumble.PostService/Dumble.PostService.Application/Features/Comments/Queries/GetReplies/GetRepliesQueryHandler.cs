using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Application.Features.Common;
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
        var cursor = CursorParsing.ParseUtcCursor(request.Cursor);

        var replies = await _commentRepository.GetRepliesAsync(request.ParentCommentId, cursor, request.Limit + 1, ct);
        var hasMore = replies.Count > request.Limit;
        if (hasMore) replies = replies.Take(request.Limit).ToList();

        var replyIds = replies.Select(r => r.Id).ToList();
        var nestedReplyCounts = await _commentRepository.GetRepliesCountForManyAsync(replyIds, ct);

        var items = replies.Select(reply => new CommentResponse(
            reply.Id, reply.PostId, reply.AuthorId, reply.AuthorDisplayName,
            reply.AuthorProfileImage, reply.ParentCommentId, reply.Content,
            reply.Status.ToString(), reply.ReactionsCount,
            nestedReplyCounts.GetValueOrDefault(reply.Id, 0),
            reply.CreatedAt, reply.UpdatedAt
        )).ToList();

        var nextCursor = hasMore && items.Count > 0
            ? CursorParsing.FormatCursor(items.Last().CreatedAt)
            : null;

        return new CursorPagedResponse<CommentResponse>(items, nextCursor, hasMore);
    }
}
