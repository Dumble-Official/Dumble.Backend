using Dumble.SharedKernel.Common;
using Dumble.SharedKernel.Enums;

namespace Dumble.SharedKernel.Events.Posts;

/// <summary>
/// Published when a user reacts to a comment — the comment-level counterpart of
/// <see cref="PostReactedEvent"/>, so the comment's author can be notified.
/// </summary>
public record CommentReactedEvent(
    string CommentId,
    string PostId,
    string CommentAuthorId,
    string ReactorId,
    string ReactorName,
    string? ReactorImage,
    ReactionType ReactionType,
    DateTimeOffset CreatedAt
) : IntegrationEvent;
