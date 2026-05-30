using Dumble.RecommendationService.Domain.Outbox;

namespace Dumble.RecommendationService.Application.Contracts;

/// <summary>
/// Sends buffered interactions to Recombee. The single seam through which the service
/// talks to the recommendation engine, so the rest of the code never references the SDK.
/// </summary>
public interface IRecombeeClient
{
    /// <summary>
    /// Sends the interactions as a single Recombee batch. Throws on failure so the caller
    /// can leave the rows buffered and retry; a no-op for an empty list.
    /// </summary>
    Task SendInteractionsAsync(IReadOnlyList<OutboxInteraction> interactions, CancellationToken ct = default);
}
