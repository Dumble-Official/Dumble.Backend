using MediatR;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Domain.Entities;
using Dumble.SocialService.Domain.Enums;

namespace Dumble.SocialService.Application.Features.Behavior.Commands.TrackBehavior;

public class TrackBehaviorCommandHandler : IRequestHandler<TrackBehaviorCommand>
{
    private readonly IUserBehaviorRepository _behaviorRepository;

    public TrackBehaviorCommandHandler(IUserBehaviorRepository behaviorRepository)
    {
        _behaviorRepository = behaviorRepository;
    }

    public async Task Handle(TrackBehaviorCommand request, CancellationToken ct)
    {
        var behavior = new UserBehavior
        {
            Id = Guid.NewGuid(),
            UserId = request.UserId,
            PostId = request.PostId,
            EventType = Enum.Parse<BehaviorEventType>(request.EventType, true),
            EventData = request.EventData,
            CreatedAt = DateTime.UtcNow
        };

        await _behaviorRepository.CreateAsync(behavior, ct);
    }
}
