using MediatR;
using MassTransit;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Reactions;
using Dumble.PostService.Domain.Entities;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Enums;
using Dumble.SharedKernel.Events.Posts;

namespace Dumble.PostService.Application.Features.Comments.Commands.AddCommentReaction;

public class AddCommentReactionCommandHandler : IRequestHandler<AddCommentReactionCommand, ReactionResponse>
{
    private readonly ICommentReactionRepository _commentReactionRepository;
    private readonly ICommentRepository _commentRepository;
    private readonly ILoggedInUserService _userService;
    private readonly IPublishEndpoint _publishEndpoint;

    public AddCommentReactionCommandHandler(
        ICommentReactionRepository commentReactionRepository,
        ICommentRepository commentRepository,
        ILoggedInUserService userService,
        IPublishEndpoint publishEndpoint)
    {
        _commentReactionRepository = commentReactionRepository;
        _commentRepository = commentRepository;
        _userService = userService;
        _publishEndpoint = publishEndpoint;
    }

    public async Task<ReactionResponse> Handle(AddCommentReactionCommand request, CancellationToken ct)
    {
        var currentUser = _userService.GetCurrentUser();
        var comment = await _commentRepository.GetByIdAsync(request.CommentId, ct)
            ?? throw new KeyNotFoundException($"Comment {request.CommentId} not found");

        var reactionType = Enum.Parse<ReactionType>(request.Type, true);
        var existing = await _commentReactionRepository.GetByCommentAndUserAsync(request.CommentId, currentUser.Id, ct);

        if (existing is not null)
        {
            existing.Type = reactionType;
            await _commentReactionRepository.UpdateAsync(existing, ct);
            await PublishReactedAsync(comment, currentUser, reactionType, ct);

            return new ReactionResponse(existing.Id, existing.UserId, currentUser.DisplayName, currentUser.ProfileImage, existing.Type.ToString(), existing.CreatedAt);
        }

        var reaction = new CommentReaction
        {
            Id = Guid.NewGuid(),
            CommentId = request.CommentId,
            UserId = currentUser.Id,
            Type = reactionType,
            CreatedAt = DateTime.UtcNow
        };

        await _commentReactionRepository.CreateAsync(reaction, ct);
        await _commentRepository.IncrementReactionsAsync(comment.Id, ct);
        await PublishReactedAsync(comment, currentUser, reactionType, ct);

        return new ReactionResponse(reaction.Id, reaction.UserId, currentUser.DisplayName, currentUser.ProfileImage, reaction.Type.ToString(), reaction.CreatedAt);
    }

    private Task PublishReactedAsync(Comment comment, CurrentUser currentUser, ReactionType reactionType, CancellationToken ct) =>
        _publishEndpoint.Publish(new CommentReactedEvent(
            comment.Id.ToString(),
            comment.PostId.ToString(),
            comment.AuthorId,
            currentUser.Id,
            currentUser.DisplayName,
            currentUser.ProfileImage,
            reactionType,
            DateTimeOffset.UtcNow), ct);
}
