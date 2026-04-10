namespace Dumble.PostService.Contracts.Comments;

public record CommentResponse(
    Guid Id,
    Guid PostId,
    string AuthorId,
    string AuthorDisplayName,
    string? AuthorProfileImage,
    Guid? ParentCommentId,
    string Content,
    string Status,
    int ReactionsCount,
    int RepliesCount,
    DateTime CreatedAt,
    DateTime UpdatedAt
);
