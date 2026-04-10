namespace Dumble.SharedKernel.Events.Posts;

public record CommentCreatedEvent(
    string CommentId,
    string PostId,
    string PostAuthorId,
    string CommentAuthorId,
    string CommenterName,
    string? ParentCommentAuthorId,
    string Preview,
    DateTime CreatedAt
);
