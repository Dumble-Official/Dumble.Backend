using Dumble.SharedKernel.Common;
using Dumble.SharedKernel.Enums;

namespace Dumble.SharedKernel.Events.Posts;

public record PostCreatedEvent(
    string PostId,
    string AuthorId,
    UserType AuthorType,
    string? GymId,
    IReadOnlyList<string> Hashtags,
    DateTimeOffset CreatedAt
) : IntegrationEvent;
