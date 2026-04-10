using MassTransit;
using MediatR;
using Dumble.SharedKernel.Events.Social;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Domain.Entities;

namespace Dumble.SocialService.Application.Features.Follows.Commands.FollowUser;

public class FollowUserCommandHandler : IRequestHandler<FollowUserCommand>
{
    private readonly IFollowRepository _followRepository;
    private readonly IPublishEndpoint _publishEndpoint;

    public FollowUserCommandHandler(IFollowRepository followRepository, IPublishEndpoint publishEndpoint)
    {
        _followRepository = followRepository;
        _publishEndpoint = publishEndpoint;
    }

    public async Task Handle(FollowUserCommand request, CancellationToken ct)
    {
        var existing = await _followRepository.GetAsync(request.FollowerId, request.FolloweeId, ct);
        if (existing is not null) return;

        var follow = new Follow
        {
            Id = Guid.NewGuid(),
            FollowerId = request.FollowerId,
            FolloweeId = request.FolloweeId,
            FolloweeType = "User",
            CreatedAt = DateTime.UtcNow
        };

        await _followRepository.CreateAsync(follow, ct);

        await _publishEndpoint.Publish(new UserFollowedEvent(
            request.FollowerId,
            request.FollowerName,
            request.FollowerImage,
            request.FolloweeId,
            follow.CreatedAt), ct);
    }
}
