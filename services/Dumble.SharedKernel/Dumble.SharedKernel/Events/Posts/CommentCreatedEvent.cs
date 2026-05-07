using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Posts;

public record CommentCreatedEvent(
    string CommentId,
    string PostId,
    string PostAuthorId,
    string CommentAuthorId,
    string CommenterName,
    string? CommenterImage,
    string? ParentCommentAuthorId,
    string Preview,
    DateTimeOffset CreatedAt
) : IntegrationEvent;
