namespace Dumble.PostService.Contracts.Posts;

public record PostResponse(
    Guid Id,
    string AuthorId,
    string AuthorDisplayName,
    string? AuthorProfileImage,
    string AuthorType,
    string? Content,
    string? GymId,
    string Status,
    int ReactionsCount,
    int CommentsCount,
    List<string> Images,
    List<string> Hashtags,
    DateTime CreatedAt,
    DateTime UpdatedAt
);
