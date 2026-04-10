namespace Dumble.SharedKernel.Events.Posts;

public record PostReactedEvent(
    string PostId,
    string PostAuthorId,
    string ReactorId,
    string ReactorName,
    string ReactionType,
    DateTime CreatedAt
);
