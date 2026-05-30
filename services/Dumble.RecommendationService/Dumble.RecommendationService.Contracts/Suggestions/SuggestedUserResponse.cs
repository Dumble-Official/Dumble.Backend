namespace Dumble.RecommendationService.Contracts.Suggestions;

/// <summary>A "who to follow" suggestion. Profile fields are hydrated from the local
/// projection; users with no captured profile are omitted rather than shown blank.</summary>
public sealed record SuggestedUserResponse(
    string UserId,
    string DisplayName,
    string? ProfileImage);
