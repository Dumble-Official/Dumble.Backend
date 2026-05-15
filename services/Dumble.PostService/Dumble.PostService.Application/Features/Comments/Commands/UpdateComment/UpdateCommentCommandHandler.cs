using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Comments;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Enums;

namespace Dumble.PostService.Application.Features.Comments.Commands.UpdateComment;

public class UpdateCommentCommandHandler : IRequestHandler<UpdateCommentCommand, CommentResponse>
{
    private readonly ICommentRepository _commentRepository;
    private readonly ILoggedInUserService _userService;

    public UpdateCommentCommandHandler(
        ICommentRepository commentRepository,
        ILoggedInUserService userService)
    {
        _commentRepository = commentRepository;
        _userService = userService;
    }

    public async Task<CommentResponse> Handle(UpdateCommentCommand request, CancellationToken ct)
    {
        var currentUser = _userService.GetCurrentUser();
        var comment = await _commentRepository.GetByIdAsync(request.CommentId, ct)
            ?? throw new KeyNotFoundException($"Comment {request.CommentId} not found");

        var canModerate = currentUser.IsInAnyRole(UserType.Admin, UserType.Moderator);
        if (comment.AuthorId != currentUser.Id && !canModerate)
            throw new UnauthorizedAccessException("You can only edit your own comments");

        comment.Content = request.Content;
        comment.UpdatedAt = DateTime.UtcNow;
        await _commentRepository.UpdateAsync(comment, ct);

        var repliesCount = await _commentRepository.GetRepliesCountAsync(comment.Id, ct);

        return new CommentResponse(
            comment.Id, comment.PostId, comment.AuthorId, comment.AuthorDisplayName,
            comment.AuthorProfileImage, comment.ParentCommentId, comment.Content,
            comment.Status.ToString(), comment.ReactionsCount, repliesCount,
            comment.CreatedAt, comment.UpdatedAt
        );
    }
}
