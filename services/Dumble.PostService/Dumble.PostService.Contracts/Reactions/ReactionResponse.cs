namespace Dumble.PostService.Contracts.Reactions;

public record ReactionResponse(
    Guid Id,
    string UserId,
    string DisplayName,
    string? ProfileImage,
    string Type,
    DateTime CreatedAt
);

public record ReactionsSummaryResponse(
    int TotalCount,
    Dictionary<string, int> CountByType,
    string? CurrentUserReaction
);
