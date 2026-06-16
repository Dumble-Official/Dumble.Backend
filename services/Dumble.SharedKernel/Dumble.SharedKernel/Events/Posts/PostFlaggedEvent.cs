using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Posts;

/// <summary>
/// Published when a moderator flags a post. The recommendation service drops the item so a
/// moderated post stops being recommended. Unflagging restores it via the catalog reconcile
/// (the post is Active again). Scoped to recommendations on purpose — it does not delete the post.
/// </summary>
public record PostFlaggedEvent(
    string PostId
) : IntegrationEvent;
