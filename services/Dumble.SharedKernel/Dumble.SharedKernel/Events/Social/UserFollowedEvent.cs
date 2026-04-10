namespace Dumble.SharedKernel.Events.Social;

public record UserFollowedEvent(
    string FollowerId,
    string FollowerName,
    string? FollowerImage,
    string FolloweeId,
    DateTime CreatedAt
);
