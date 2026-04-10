using MassTransit;
using Dumble.SharedKernel.Events.Posts;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Domain.Entities;
using Dumble.SocialService.Domain.Enums;

namespace Dumble.SocialService.Infrastructure.Messaging.Consumers;

public class PostReactedConsumer : IConsumer<PostReactedEvent>
{
    private readonly IUserBehaviorRepository _behaviorRepository;

    public PostReactedConsumer(IUserBehaviorRepository behaviorRepository)
    {
        _behaviorRepository = behaviorRepository;
    }

    public async Task Consume(ConsumeContext<PostReactedEvent> context)
    {
        var evt = context.Message;
        await _behaviorRepository.CreateAsync(new UserBehavior
        {
            Id = Guid.NewGuid(),
            UserId = evt.ReactorId,
            PostId = evt.PostId,
            EventType = BehaviorEventType.Reaction,
            EventData = evt.ReactionType,
            CreatedAt = evt.CreatedAt
        }, context.CancellationToken);
    }
}
