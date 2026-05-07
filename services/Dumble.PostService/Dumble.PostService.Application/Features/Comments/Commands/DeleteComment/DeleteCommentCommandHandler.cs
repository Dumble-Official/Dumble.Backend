using MediatR;
using MassTransit;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Domain.Enums;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Enums;
using Dumble.SharedKernel.Events.Posts;

namespace Dumble.PostService.Application.Features.Comments.Commands.DeleteComment;

public class DeleteCommentCommandHandler : IRequestHandler<DeleteCommentCommand>
{
    private readonly ICommentRepository _commentRepository;
    private readonly IPostRepository _postRepository;
    private readonly ILoggedInUserService _userService;
    private readonly IPublishEndpoint _publishEndpoint;

    public DeleteCommentCommandHandler(
        ICommentRepository commentRepository,
        IPostRepository postRepository,
        ILoggedInUserService userService,
        IPublishEndpoint publishEndpoint)
    {
        _commentRepository = commentRepository;
        _postRepository = postRepository;
        _userService = userService;
        _publishEndpoint = publishEndpoint;
    }

    public async Task Handle(DeleteCommentCommand request, CancellationToken ct)
    {
        var currentUser = _userService.GetCurrentUser();
        var comment = await _commentRepository.GetByIdAsync(request.CommentId, ct)
            ?? throw new KeyNotFoundException($"Comment {request.CommentId} not found");

        var canModerate = currentUser.IsInAnyRole(UserType.Admin, UserType.Moderator);
        if (comment.AuthorId != currentUser.Id && !canModerate)
            throw new UnauthorizedAccessException("You can only delete your own comments");

        comment.Status = CommentStatus.Deleted;
        comment.UpdatedAt = DateTime.UtcNow;
        await _commentRepository.UpdateAsync(comment, ct);

        await _postRepository.DecrementCommentsAsync(comment.PostId, ct);
        var post = await _postRepository.GetByIdAsync(comment.PostId, ct);

        await _publishEndpoint.Publish(new CommentDeletedEvent(
            comment.Id.ToString(),
            comment.PostId.ToString(),
            post?.AuthorId ?? string.Empty,
            comment.AuthorId
        ), ct);
    }
}
