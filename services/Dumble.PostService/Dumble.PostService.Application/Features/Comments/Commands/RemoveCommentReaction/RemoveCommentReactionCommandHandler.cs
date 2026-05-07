using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.SharedKernel.Contracts;

namespace Dumble.PostService.Application.Features.Comments.Commands.RemoveCommentReaction;

public class RemoveCommentReactionCommandHandler : IRequestHandler<RemoveCommentReactionCommand>
{
    private readonly ICommentReactionRepository _commentReactionRepository;
    private readonly ICommentRepository _commentRepository;
    private readonly ILoggedInUserService _userService;

    public RemoveCommentReactionCommandHandler(
        ICommentReactionRepository commentReactionRepository,
        ICommentRepository commentRepository,
        ILoggedInUserService userService)
    {
        _commentReactionRepository = commentReactionRepository;
        _commentRepository = commentRepository;
        _userService = userService;
    }

    public async Task Handle(RemoveCommentReactionCommand request, CancellationToken ct)
    {
        var currentUser = _userService.GetCurrentUser();
        var reaction = await _commentReactionRepository.GetByCommentAndUserAsync(request.CommentId, currentUser.Id, ct)
            ?? throw new KeyNotFoundException("Reaction not found");

        await _commentReactionRepository.DeleteAsync(reaction, ct);

        var comment = await _commentRepository.GetByIdAsync(request.CommentId, ct);
        if (comment is not null && comment.ReactionsCount > 0)
        {
            comment.ReactionsCount--;
            await _commentRepository.UpdateAsync(comment, ct);
        }
    }
}
