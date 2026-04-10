namespace Dumble.SharedKernel.Events.Posts;

public record PostCreatedEvent(
    string PostId,
    string AuthorId,
    string AuthorType,
    string? GymId,
    List<string> Hashtags,
    DateTime CreatedAt
);
