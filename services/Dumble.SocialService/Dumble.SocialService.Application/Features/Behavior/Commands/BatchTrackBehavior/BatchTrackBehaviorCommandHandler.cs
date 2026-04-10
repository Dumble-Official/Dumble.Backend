using MediatR;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Domain.Entities;
using Dumble.SocialService.Domain.Enums;

namespace Dumble.SocialService.Application.Features.Behavior.Commands.BatchTrackBehavior;

public class BatchTrackBehaviorCommandHandler : IRequestHandler<BatchTrackBehaviorCommand>
{
    private readonly IUserBehaviorRepository _behaviorRepository;

    public BatchTrackBehaviorCommandHandler(IUserBehaviorRepository behaviorRepository)
    {
        _behaviorRepository = behaviorRepository;
    }

    public async Task Handle(BatchTrackBehaviorCommand request, CancellationToken ct)
    {
        var behaviors = request.Events.Select(e => new UserBehavior
        {
            Id = Guid.NewGuid(),
            UserId = request.UserId,
            PostId = e.PostId,
            EventType = Enum.Parse<BehaviorEventType>(e.EventType, true),
            EventData = e.EventData,
            CreatedAt = DateTime.UtcNow
        }).ToList();

        await _behaviorRepository.CreateBatchAsync(behaviors, ct);
    }
}
