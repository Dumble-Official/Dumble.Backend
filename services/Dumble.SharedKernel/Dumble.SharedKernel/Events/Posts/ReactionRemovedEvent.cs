namespace Dumble.SharedKernel.Events.Posts;

public record ReactionRemovedEvent(
    string PostId,
    string ReactorId
);
