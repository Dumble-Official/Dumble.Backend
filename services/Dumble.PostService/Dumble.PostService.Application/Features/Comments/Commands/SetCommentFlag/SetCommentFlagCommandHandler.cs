using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Domain.Enums;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Enums;

namespace Dumble.PostService.Application.Features.Comments.Commands.SetCommentFlag;

public class SetCommentFlagCommandHandler : IRequestHandler<SetCommentFlagCommand>
{
    private readonly ICommentRepository _commentRepository;
    private readonly ILoggedInUserService _userService;

    public SetCommentFlagCommandHandler(ICommentRepository commentRepository, ILoggedInUserService userService)
    {
        _commentRepository = commentRepository;
        _userService = userService;
    }

    public async Task Handle(SetCommentFlagCommand request, CancellationToken ct)
    {
        var currentUser = _userService.GetCurrentUser();
        if (!currentUser.IsInAnyRole(UserType.Admin, UserType.Moderator))
            throw new UnauthorizedAccessException("Only a moderator or admin can flag comments");

        var comment = await _commentRepository.GetByIdAsync(request.CommentId, ct);
        if (comment is null || comment.Status == CommentStatus.Deleted)
            throw new KeyNotFoundException($"Comment {request.CommentId} not found");

        comment.Status = request.Flagged ? CommentStatus.Flagged : CommentStatus.Active;
        comment.UpdatedAt = DateTime.UtcNow;
        await _commentRepository.UpdateAsync(comment, ct);
    }
}
