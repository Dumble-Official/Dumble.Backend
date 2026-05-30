namespace Dumble.PostService.Contracts.Posts;

/// <summary>
/// Lean projection of a post for catalog scans — only the fields a downstream catalog
/// (e.g. the recommendation service's Recombee mirror) needs to reconcile against. Keeps the
/// payload small so a full-table sweep stays cheap, and deliberately omits content/images.
/// </summary>
public record PostCatalogItem(
    Guid Id,
    string AuthorId,
    string AuthorType,
    string? GymId,
    List<string> Hashtags,
    DateTime CreatedAt
);
