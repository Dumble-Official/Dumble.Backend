using Dumble.RecommendationService.Domain.Outbox;

namespace Dumble.RecommendationService.Application.Contracts;

/// <summary>
/// Persists buffered interactions. Implementations must be idempotent on
/// <see cref="OutboxInteraction.SourceEventId"/>: an interaction whose source event id
/// is already buffered is a no-op (returns <c>false</c>) so redelivered domain events
/// don't double-count.
/// </summary>
public interface IInteractionOutboxWriter
{
    /// <returns><c>true</c> if a new row was buffered; <c>false</c> if it was a duplicate and ignored.</returns>
    Task<bool> AddAsync(OutboxInteraction interaction, CancellationToken ct = default);
}
