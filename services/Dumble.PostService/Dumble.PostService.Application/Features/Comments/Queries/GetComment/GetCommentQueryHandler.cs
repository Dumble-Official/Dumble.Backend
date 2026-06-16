using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Comments;
using Dumble.PostService.Domain.Enums;

namespace Dumble.PostService.Application.Features.Comments.Queries.GetComment;

public class GetCommentQueryHandler : IRequestHandler<GetCommentQuery, CommentResponse>
{
    private readonly ICommentRepository _commentRepository;

    public GetCommentQueryHandler(ICommentRepository commentRepository)
    {
        _commentRepository = commentRepository;
    }

    public async Task<CommentResponse> Handle(GetCommentQuery request, CancellationToken ct)
    {
        var comment = await _commentRepository.GetByIdAsync(request.CommentId, ct);
        if (comment is null || comment.Status == CommentStatus.Deleted)
            throw new KeyNotFoundException($"Comment {request.CommentId} not found");

        var repliesCount = await _commentRepository.GetRepliesCountAsync(comment.Id, ct);

        return new CommentResponse(
            comment.Id, comment.PostId, comment.AuthorId, comment.AuthorDisplayName,
            comment.AuthorProfileImage, comment.ParentCommentId, comment.Content,
            comment.Status.ToString(), comment.ReactionsCount, repliesCount,
            comment.CreatedAt, comment.UpdatedAt);
    }
}
