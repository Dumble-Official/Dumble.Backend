using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Social;

public record UserFollowedEvent(
    string FollowerId,
    string FollowerName,
    string? FollowerImage,
    string FolloweeId,
    DateTimeOffset CreatedAt
) : IntegrationEvent;
