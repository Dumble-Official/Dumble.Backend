using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Reactions;
using Dumble.PostService.Domain.Entities;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Enums;

namespace Dumble.PostService.Application.Features.Comments.Commands.AddCommentReaction;

public class AddCommentReactionCommandHandler : IRequestHandler<AddCommentReactionCommand, ReactionResponse>
{
    private readonly ICommentReactionRepository _commentReactionRepository;
    private readonly ICommentRepository _commentRepository;
    private readonly ILoggedInUserService _userService;

    public AddCommentReactionCommandHandler(
        ICommentReactionRepository commentReactionRepository,
        ICommentRepository commentRepository,
        ILoggedInUserService userService)
    {
        _commentReactionRepository = commentReactionRepository;
        _commentRepository = commentRepository;
        _userService = userService;
    }

    public async Task<ReactionResponse> Handle(AddCommentReactionCommand request, CancellationToken ct)
    {
        var currentUser = await _userService.GetCurrentUserAsync(ct);
        var comment = await _commentRepository.GetByIdAsync(request.CommentId, ct)
            ?? throw new KeyNotFoundException($"Comment {request.CommentId} not found");

        var reactionType = Enum.Parse<ReactionType>(request.Type, true);
        var existing = await _commentReactionRepository.GetByCommentAndUserAsync(request.CommentId, currentUser.Id, ct);

        if (existing is not null)
        {
            existing.Type = reactionType;
            await _commentReactionRepository.UpdateAsync(existing, ct);

            return new ReactionResponse(existing.Id, existing.UserId, existing.Type.ToString(), existing.CreatedAt);
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

        comment.ReactionsCount++;
        await _commentRepository.UpdateAsync(comment, ct);

        return new ReactionResponse(reaction.Id, reaction.UserId, reaction.Type.ToString(), reaction.CreatedAt);
    }
}
