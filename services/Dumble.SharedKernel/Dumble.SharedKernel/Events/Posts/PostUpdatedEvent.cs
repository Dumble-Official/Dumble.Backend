using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Posts;

public record PostUpdatedEvent(
    string PostId,
    string AuthorId,
    IReadOnlyList<string> Hashtags,
    DateTimeOffset UpdatedAt
) : IntegrationEvent;
