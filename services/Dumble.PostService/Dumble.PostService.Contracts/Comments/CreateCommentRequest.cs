namespace Dumble.PostService.Contracts.Comments;

public record CreateCommentRequest(
    string Content,
    Guid? ParentCommentId
);
