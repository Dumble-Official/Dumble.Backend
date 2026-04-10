namespace Dumble.SocialService.Contracts.Follows;

public record BatchFollowStatusResponse(Dictionary<string, bool> Statuses);
