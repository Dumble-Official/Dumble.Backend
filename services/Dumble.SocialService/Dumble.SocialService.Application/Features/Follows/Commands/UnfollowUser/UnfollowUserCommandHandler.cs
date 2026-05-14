using MassTransit;
using MediatR;
using Dumble.SharedKernel.Events.Social;
using Dumble.SocialService.Application.Contracts;

namespace Dumble.SocialService.Application.Features.Follows.Commands.UnfollowUser;

public class UnfollowUserCommandHandler : IRequestHandler<UnfollowUserCommand>
{
    private readonly IFollowRepository _followRepository;
    private readonly IFeedCacheService _feedCache;
    private readonly IPublishEndpoint _publishEndpoint;

    public UnfollowUserCommandHandler(
        IFollowRepository followRepository,
        IFeedCacheService feedCache,
        IPublishEndpoint publishEndpoint)
    {
        _followRepository = followRepository;
        _feedCache = feedCache;
        _publishEndpoint = publishEndpoint;
    }

    public async Task Handle(UnfollowUserCommand request, CancellationToken ct)
    {
        var deleted = await _followRepository.DeleteAsync(request.FollowerId, request.FolloweeId, ct);
        if (deleted)
        {
            // Invalidate the follower's cached feed so the followee's posts
            // are removed on next read. Without this they linger for up to
            // the cache TTL.
            await _feedCache.InvalidateFeedAsync(request.FollowerId, ct);

            await _publishEndpoint.Publish(new UserUnfollowedEvent(request.FollowerId, request.FolloweeId), ct);
        }
    }
}
