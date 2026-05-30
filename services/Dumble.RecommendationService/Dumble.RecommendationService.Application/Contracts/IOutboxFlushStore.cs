using Dumble.RecommendationService.Domain.Outbox;

namespace Dumble.RecommendationService.Application.Contracts;

/// <summary>
/// Storage operations the flush worker needs. The implementation claims rows atomically
/// (so concurrent workers never process the same interaction) and reflects the outcome.
/// </summary>
public interface IOutboxFlushStore
{
    /// <summary>
    /// Atomically claim up to <paramref name="batchSize"/> pending rows (oldest first),
    /// moving them to Processing, and return them. Replica-safe via FOR UPDATE SKIP LOCKED.
    /// </summary>
    Task<IReadOnlyList<OutboxInteraction>> ClaimPendingAsync(int batchSize, CancellationToken ct = default);

    /// <summary>Mark a successfully-flushed batch as sent.</summary>
    Task MarkSentAsync(IReadOnlyList<OutboxInteraction> sent, CancellationToken ct = default);

    /// <summary>Return a failed batch to the queue (Processing -> Pending) and count the attempt.</summary>
    Task ReturnToPendingAsync(IReadOnlyList<OutboxInteraction> failed, CancellationToken ct = default);

    /// <summary>Backlog snapshot (pending count + oldest pending time) for the flush-health canary.</summary>
    Task<OutboxBacklog> GetBacklogAsync(CancellationToken ct = default);
}
