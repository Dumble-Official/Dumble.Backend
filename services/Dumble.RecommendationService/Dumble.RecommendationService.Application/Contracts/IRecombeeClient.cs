using Dumble.RecommendationService.Domain.Outbox;

namespace Dumble.RecommendationService.Application.Contracts;

/// <summary>
/// The only seam through which the service talks to Recombee — interactions and the item
/// catalog. Keeps the SDK out of the rest of the codebase.
/// </summary>
public interface IRecombeeClient
{
    /// <summary>
    /// Sends the interactions as a single Recombee batch. Throws on failure so the caller
    /// can leave the rows buffered and retry; a no-op for an empty list.
    /// </summary>
    Task SendInteractionsAsync(IReadOnlyList<OutboxInteraction> interactions, CancellationToken ct = default);

    /// <summary>Create or partially update an item's properties (cascadeCreate).</summary>
    Task UpsertItemAsync(RecombeeItemUpsert item, CancellationToken ct = default);

    /// <summary>
    /// Upsert a page of items in a single batch — used by the catalog reconcile to heal drift
    /// efficiently. Throws on failure so the caller can retry the page; a no-op for an empty list.
    /// </summary>
    Task UpsertItemsAsync(IReadOnlyList<RecombeeItemUpsert> items, CancellationToken ct = default);

    /// <summary>Hard-delete an item so it can never be recommended again (D11).</summary>
    Task DeleteItemAsync(string itemId, CancellationToken ct = default);

    /// <summary>
    /// Hard-delete a user and all of their interactions — right-to-be-forgotten (RTBF). After this
    /// the user contributes nothing to the model and can never be recommended.
    /// </summary>
    Task DeleteUserAsync(string userId, CancellationToken ct = default);

    /// <summary>
    /// Every item id currently in the catalog — used by the orphan sweep to find items that
    /// outlived their source post (e.g. a dropped delete event). Paged internally.
    /// </summary>
    Task<IReadOnlyList<string>> ListItemIdsAsync(CancellationToken ct = default);

    /// <summary>Idempotently ensure the item property schema exists. Safe to call repeatedly.</summary>
    Task EnsureSchemaAsync(CancellationToken ct = default);

    /// <summary>Personalized post recommendations for a user — returns ranked item ids only.</summary>
    Task<IReadOnlyList<string>> RecommendItemsToUserAsync(string userId, int count, CancellationToken ct = default);

    /// <summary>
    /// Personalized recommendations restricted to posts authored by the given users — the home
    /// feed (ranked posts from people the caller follows). Same engine as the explore feed,
    /// filtered to the followee set. Returns ranked item ids only.
    /// </summary>
    Task<IReadOnlyList<string>> RecommendFollowedItemsAsync(
        string userId, int count, IReadOnlyCollection<string> authorIds, CancellationToken ct = default);

    /// <summary>Recommend users to follow — returns ranked user ids, by interaction similarity.</summary>
    Task<IReadOnlyList<string>> RecommendUsersToUserAsync(string userId, int count, CancellationToken ct = default);
}
