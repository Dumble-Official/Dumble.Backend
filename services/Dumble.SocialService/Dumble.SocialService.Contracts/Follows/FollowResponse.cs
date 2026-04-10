namespace Dumble.SocialService.Contracts.Follows;

public record FollowResponse(
    string UserId,
    string DisplayName,
    string? ProfileImage,
    DateTime FollowedAt
);
