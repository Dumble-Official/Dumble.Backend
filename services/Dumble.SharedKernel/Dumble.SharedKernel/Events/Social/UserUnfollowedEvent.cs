using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Social;

public record UserUnfollowedEvent(
    string FollowerId,
    string FolloweeId
) : IntegrationEvent;
