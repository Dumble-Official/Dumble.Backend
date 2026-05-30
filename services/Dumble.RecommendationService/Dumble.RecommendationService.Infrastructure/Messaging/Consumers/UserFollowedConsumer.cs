using Dumble.RecommendationService.Application.Contracts;
using Dumble.SharedKernel.Events.Social;
using MassTransit;

namespace Dumble.RecommendationService.Infrastructure.Messaging.Consumers;

/// <summary>
/// Maintains the follow projection (for suggestion exclusion) and harvests the follower's
/// profile (name/avatar) for suggestion hydration.
/// </summary>
public sealed class UserFollowedConsumer : IConsumer<UserFollowedEvent>
{
    private readonly IFollowProjection _follows;
    private readonly IUserProfileProjection _profiles;

    public UserFollowedConsumer(IFollowProjection follows, IUserProfileProjection profiles)
    {
        _follows = follows;
        _profiles = profiles;
    }

    public async Task Consume(ConsumeContext<UserFollowedEvent> context)
    {
        var e = context.Message;
        await _follows.AddFollowAsync(e.FollowerId, e.FolloweeId, context.CancellationToken);
        await _profiles.SetAsync(e.FollowerId, e.FollowerName, e.FollowerImage, context.CancellationToken);
    }
}
