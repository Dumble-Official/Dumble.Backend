using MediatR;
using MassTransit;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Domain.Enums;
using Dumble.SharedKernel.Contracts;
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
        var currentUser = await _userService.GetCurrentUserAsync(ct);
        var comment = await _commentRepository.GetByIdAsync(request.CommentId, ct)
            ?? throw new KeyNotFoundException($"Comment {request.CommentId} not found");

        if (comment.AuthorId != currentUser.Id)
            throw new UnauthorizedAccessException("You can only delete your own comments");

        comment.Status = CommentStatus.Deleted;
        comment.UpdatedAt = DateTime.UtcNow;
        await _commentRepository.UpdateAsync(comment, ct);

        var post = await _postRepository.GetByIdAsync(comment.PostId, ct);
        if (post is not null && post.CommentsCount > 0)
        {
            post.CommentsCount--;
            await _postRepository.UpdateAsync(post, ct);
        }

        await _publishEndpoint.Publish(new CommentDeletedEvent(
            comment.Id.ToString(),
            comment.PostId.ToString()
        ), ct);
    }
}
