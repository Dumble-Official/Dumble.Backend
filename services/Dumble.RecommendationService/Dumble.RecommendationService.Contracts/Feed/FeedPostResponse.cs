namespace Dumble.RecommendationService.Contracts.Feed;

/// <summary>A hydrated post for the explore feed. Same shape SocialService returns, so the
/// client renders explore identically after the move.</summary>
public sealed record FeedPostResponse(
    string Id,
    string AuthorId,
    string AuthorDisplayName,
    string? AuthorProfileImage,
    string? Content,
    List<string> Images,
    int ReactionsCount,
    int CommentsCount,
    DateTime CreatedAt);
