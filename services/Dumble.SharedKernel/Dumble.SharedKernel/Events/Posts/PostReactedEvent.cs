using Dumble.SharedKernel.Common;
using Dumble.SharedKernel.Enums;

namespace Dumble.SharedKernel.Events.Posts;

public record PostReactedEvent(
    string PostId,
    string PostAuthorId,
    string ReactorId,
    string ReactorName,
    string? ReactorImage,
    ReactionType ReactionType,
    DateTimeOffset CreatedAt
) : IntegrationEvent;
