using MassTransit;
using MediatR;
using Dumble.SharedKernel.Events.Social;
using Dumble.SocialService.Application.Contracts;

namespace Dumble.SocialService.Application.Features.Follows.Commands.UnfollowUser;

public class UnfollowUserCommandHandler : IRequestHandler<UnfollowUserCommand>
{
    private readonly IFollowRepository _followRepository;
    private readonly IPublishEndpoint _publishEndpoint;

    public UnfollowUserCommandHandler(
        IFollowRepository followRepository,
        IPublishEndpoint publishEndpoint)
    {
        _followRepository = followRepository;
        _publishEndpoint = publishEndpoint;
    }

    public async Task Handle(UnfollowUserCommand request, CancellationToken ct)
    {
        var deleted = await _followRepository.DeleteAsync(request.FollowerId, request.FolloweeId, ct);
        if (deleted)
        {
            // The recommendation service consumes UserUnfollowedEvent to update its follow
            // projection, dropping the unfollowed author from the Recombee-ranked home feed.
            await _publishEndpoint.Publish(new UserUnfollowedEvent(request.FollowerId, request.FolloweeId), ct);
        }
    }
}
