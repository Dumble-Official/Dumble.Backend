namespace Dumble.RecommendationService.Contracts.Common;

/// <summary>Cursor-paginated response. Same shape SocialService uses, so the app's feed
/// paging code is unchanged when explore moves here.</summary>
public sealed record CursorPagedResponse<T>(
    List<T> Items,
    string? NextCursor,
    bool HasMore);
