using MassTransit;
using Dumble.SharedKernel.Events.Posts;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Domain.Entities;
using Dumble.SocialService.Domain.Enums;

namespace Dumble.SocialService.Infrastructure.Messaging.Consumers;

public class CommentCreatedConsumer : IConsumer<CommentCreatedEvent>
{
    private readonly IUserBehaviorRepository _behaviorRepository;

    public CommentCreatedConsumer(IUserBehaviorRepository behaviorRepository)
    {
        _behaviorRepository = behaviorRepository;
    }

    public async Task Consume(ConsumeContext<CommentCreatedEvent> context)
    {
        var evt = context.Message;
        await _behaviorRepository.CreateAsync(new UserBehavior
        {
            Id = Guid.NewGuid(),
            UserId = evt.CommentAuthorId,
            PostId = evt.PostId,
            EventType = BehaviorEventType.Comment,
            CreatedAt = evt.CreatedAt
        }, context.CancellationToken);
    }
}
