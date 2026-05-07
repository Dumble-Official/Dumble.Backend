using MediatR;
using MassTransit;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Comments;
using Dumble.PostService.Domain.Entities;
using Dumble.PostService.Domain.Enums;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Events.Posts;

namespace Dumble.PostService.Application.Features.Comments.Commands.CreateComment;

public class CreateCommentCommandHandler : IRequestHandler<CreateCommentCommand, CommentResponse>
{
    private readonly ICommentRepository _commentRepository;
    private readonly IPostRepository _postRepository;
    private readonly ILoggedInUserService _userService;
    private readonly IPublishEndpoint _publishEndpoint;

    public CreateCommentCommandHandler(
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

    public async Task<CommentResponse> Handle(CreateCommentCommand request, CancellationToken ct)
    {
        var currentUser = _userService.GetCurrentUser();
        var post = await _postRepository.GetByIdAsync(request.PostId, ct)
            ?? throw new KeyNotFoundException($"Post {request.PostId} not found");

        string? parentCommentAuthorId = null;
        if (request.ParentCommentId is not null)
        {
            var parentComment = await _commentRepository.GetByIdAsync(request.ParentCommentId.Value, ct)
                ?? throw new KeyNotFoundException($"Parent comment {request.ParentCommentId} not found");
            parentCommentAuthorId = parentComment.AuthorId;
        }

        var comment = new Comment
        {
            Id = Guid.NewGuid(),
            PostId = request.PostId,
            AuthorId = currentUser.Id,
            AuthorDisplayName = currentUser.DisplayName,
            AuthorProfileImage = currentUser.ProfileImage,
            ParentCommentId = request.ParentCommentId,
            Content = request.Content,
            Status = CommentStatus.Active,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        await _commentRepository.CreateAsync(comment, ct);

        post.CommentsCount++;
        await _postRepository.UpdateAsync(post, ct);

        var preview = comment.Content.Length > 100
            ? comment.Content[..100] + "..."
            : comment.Content;

        await _publishEndpoint.Publish(new CommentCreatedEvent(
            comment.Id.ToString(),
            post.Id.ToString(),
            post.AuthorId,
            currentUser.Id,
            currentUser.DisplayName,
            currentUser.ProfileImage,
            parentCommentAuthorId,
            preview,
            new DateTimeOffset(comment.CreatedAt, TimeSpan.Zero)
        ), ct);

        var repliesCount = 0;
        return new CommentResponse(
            comment.Id, comment.PostId, comment.AuthorId, comment.AuthorDisplayName,
            comment.AuthorProfileImage, comment.ParentCommentId, comment.Content,
            comment.Status.ToString(), comment.ReactionsCount, repliesCount,
            comment.CreatedAt, comment.UpdatedAt
        );
    }
}
