using MassTransit;
using MediatR;
using Dumble.SharedKernel.Events.Social;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Domain.Entities;

namespace Dumble.SocialService.Application.Features.Follows.Commands.FollowUser;

public class FollowUserCommandHandler : IRequestHandler<FollowUserCommand>
{
    private readonly IFollowRepository _followRepository;
    private readonly IFeedCacheService _feedCache;
    private readonly IPublishEndpoint _publishEndpoint;

    public FollowUserCommandHandler(
        IFollowRepository followRepository,
        IFeedCacheService feedCache,
        IPublishEndpoint publishEndpoint)
    {
        _followRepository = followRepository;
        _feedCache = feedCache;
        _publishEndpoint = publishEndpoint;
    }

    public async Task Handle(FollowUserCommand request, CancellationToken ct)
    {
        if (request.FollowerId == request.FolloweeId)
            throw new ArgumentException("You cannot follow yourself");

        var existing = await _followRepository.GetAsync(request.FollowerId, request.FolloweeId, ct);
        if (existing is not null) return;

        var follow = new Follow
        {
            Id = Guid.NewGuid(),
            FollowerId = request.FollowerId,
            FollowerName = request.FollowerName,
            FollowerImage = request.FollowerImage,
            FolloweeId = request.FolloweeId,
            FolloweeType = "User",
            CreatedAt = DateTime.UtcNow
        };

        await _followRepository.CreateAsync(follow, ct);

        // Invalidate the follower's cached feed so the next /api/feed call
        // rebuilds it from the followee's posts. Without this, a fresh
        // follow takes up to the cache TTL to surface in the user's feed.
        await _feedCache.InvalidateFeedAsync(request.FollowerId, ct);

        await _publishEndpoint.Publish(new UserFollowedEvent(
            request.FollowerId,
            request.FollowerName,
            request.FollowerImage,
            request.FolloweeId,
            new DateTimeOffset(follow.CreatedAt, TimeSpan.Zero)), ct);
    }
}
