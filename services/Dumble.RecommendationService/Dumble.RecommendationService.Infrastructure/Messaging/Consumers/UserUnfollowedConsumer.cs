using Dumble.RecommendationService.Application.Contracts;
using Dumble.SharedKernel.Events.Social;
using MassTransit;

namespace Dumble.RecommendationService.Infrastructure.Messaging.Consumers;

/// <summary>Keeps the follow projection in step when a user unfollows.</summary>
public sealed class UserUnfollowedConsumer : IConsumer<UserUnfollowedEvent>
{
    private readonly IFollowProjection _follows;

    public UserUnfollowedConsumer(IFollowProjection follows) => _follows = follows;

    public Task Consume(ConsumeContext<UserUnfollowedEvent> context)
    {
        var e = context.Message;
        return _follows.RemoveFollowAsync(e.FollowerId, e.FolloweeId, context.CancellationToken);
    }
}
