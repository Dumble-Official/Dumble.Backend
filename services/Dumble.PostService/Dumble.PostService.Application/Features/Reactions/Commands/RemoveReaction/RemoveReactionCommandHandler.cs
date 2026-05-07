using MediatR;
using MassTransit;
using Dumble.PostService.Application.Contracts;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Events.Posts;

namespace Dumble.PostService.Application.Features.Reactions.Commands.RemoveReaction;

public class RemoveReactionCommandHandler : IRequestHandler<RemoveReactionCommand>
{
    private readonly IReactionRepository _reactionRepository;
    private readonly IPostRepository _postRepository;
    private readonly ILoggedInUserService _userService;
    private readonly IPublishEndpoint _publishEndpoint;

    public RemoveReactionCommandHandler(
        IReactionRepository reactionRepository,
        IPostRepository postRepository,
        ILoggedInUserService userService,
        IPublishEndpoint publishEndpoint)
    {
        _reactionRepository = reactionRepository;
        _postRepository = postRepository;
        _userService = userService;
        _publishEndpoint = publishEndpoint;
    }

    public async Task Handle(RemoveReactionCommand request, CancellationToken ct)
    {
        var currentUser = _userService.GetCurrentUser();
        var reaction = await _reactionRepository.GetByPostAndUserAsync(request.PostId, currentUser.Id, ct)
            ?? throw new KeyNotFoundException("Reaction not found");

        await _reactionRepository.DeleteAsync(reaction, ct);
        await _postRepository.DecrementReactionsAsync(request.PostId, ct);

        var post = await _postRepository.GetByIdAsync(request.PostId, ct);

        await _publishEndpoint.Publish(new ReactionRemovedEvent(
            request.PostId.ToString(),
            post?.AuthorId ?? string.Empty,
            currentUser.Id
        ), ct);
    }
}
