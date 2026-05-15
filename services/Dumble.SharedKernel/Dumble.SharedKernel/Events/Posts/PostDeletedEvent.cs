using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Posts;

public record PostDeletedEvent(
    string PostId,
    string AuthorId
) : IntegrationEvent;
