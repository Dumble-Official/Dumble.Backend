namespace Dumble.SharedKernel.Events.Posts;

public record PostDeletedEvent(
    string PostId,
    string AuthorId
);
