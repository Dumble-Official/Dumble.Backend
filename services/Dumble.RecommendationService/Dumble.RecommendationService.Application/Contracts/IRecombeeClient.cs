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

    /// <summary>Hard-delete an item so it can never be recommended again (D11).</summary>
    Task DeleteItemAsync(string itemId, CancellationToken ct = default);

    /// <summary>Idempotently ensure the item property schema exists. Safe to call repeatedly.</summary>
    Task EnsureSchemaAsync(CancellationToken ct = default);

    /// <summary>Personalized post recommendations for a user — returns ranked item ids only.</summary>
    Task<IReadOnlyList<string>> RecommendItemsToUserAsync(string userId, int count, CancellationToken ct = default);
}
