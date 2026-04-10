using MediatR;
using MassTransit;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Reactions;
using Dumble.PostService.Domain.Entities;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Enums;
using Dumble.SharedKernel.Events.Posts;

namespace Dumble.PostService.Application.Features.Reactions.Commands.AddReaction;

public class AddReactionCommandHandler : IRequestHandler<AddReactionCommand, ReactionResponse>
{
    private readonly IReactionRepository _reactionRepository;
    private readonly IPostRepository _postRepository;
    private readonly ILoggedInUserService _userService;
    private readonly IPublishEndpoint _publishEndpoint;

    public AddReactionCommandHandler(
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

    public async Task<ReactionResponse> Handle(AddReactionCommand request, CancellationToken ct)
    {
        var currentUser = await _userService.GetCurrentUserAsync(ct);
        var post = await _postRepository.GetByIdAsync(request.PostId, ct)
            ?? throw new KeyNotFoundException($"Post {request.PostId} not found");

        var reactionType = Enum.Parse<ReactionType>(request.Type, true);
        var existing = await _reactionRepository.GetByPostAndUserAsync(request.PostId, currentUser.Id, ct);

        if (existing is not null)
        {
            existing.Type = reactionType;
            await _reactionRepository.UpdateAsync(existing, ct);

            return new ReactionResponse(existing.Id, existing.UserId, existing.Type.ToString(), existing.CreatedAt);
        }

        var reaction = new Reaction
        {
            Id = Guid.NewGuid(),
            PostId = request.PostId,
            UserId = currentUser.Id,
            Type = reactionType,
            CreatedAt = DateTime.UtcNow
        };

        await _reactionRepository.CreateAsync(reaction, ct);

        post.ReactionsCount++;
        await _postRepository.UpdateAsync(post, ct);

        await _publishEndpoint.Publish(new PostReactedEvent(
            post.Id.ToString(),
            post.AuthorId,
            currentUser.Id,
            currentUser.DisplayName,
            reactionType.ToString(),
            reaction.CreatedAt
        ), ct);

        return new ReactionResponse(reaction.Id, reaction.UserId, reaction.Type.ToString(), reaction.CreatedAt);
    }
}
