using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Posts;

public record CommentDeletedEvent(
    string CommentId,
    string PostId,
    string PostAuthorId,
    string CommentAuthorId
) : IntegrationEvent;
