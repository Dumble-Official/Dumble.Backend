namespace Dumble.SocialService.Contracts.Feed;

public record FeedPostResponse(
    string Id,
    string AuthorId,
    string AuthorDisplayName,
    string? AuthorProfileImage,
    string? Content,
    List<string> Images,
    int ReactionsCount,
    int CommentsCount,
    DateTime CreatedAt
);
