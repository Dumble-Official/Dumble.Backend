namespace Dumble.SharedKernel.Events.Posts;

public record CommentDeletedEvent(
    string CommentId,
    string PostId
);
