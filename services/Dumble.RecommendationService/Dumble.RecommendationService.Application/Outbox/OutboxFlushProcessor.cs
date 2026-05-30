using Dumble.RecommendationService.Application.Contracts;
using Microsoft.Extensions.Logging;

namespace Dumble.RecommendationService.Application.Outbox;

/// <summary>
/// One flush cycle: claim a batch of pending interactions, send them to Recombee, and mark
/// them sent. On failure the batch is returned to the queue and the error rethrown so the
/// worker can back off. Pure orchestration over the two seams, so it is fully unit-testable.
/// </summary>
public sealed class OutboxFlushProcessor
{
    private readonly IOutboxFlushStore _store;
    private readonly IRecombeeClient _recombee;
    private readonly ILogger<OutboxFlushProcessor> _logger;

    public OutboxFlushProcessor(
        IOutboxFlushStore store,
        IRecombeeClient recombee,
        ILogger<OutboxFlushProcessor> logger)
    {
        _store = store;
        _recombee = recombee;
        _logger = logger;
    }

    /// <returns>The number of interactions flushed this cycle (0 when nothing was pending).</returns>
    public async Task<int> FlushOnceAsync(int batchSize, CancellationToken ct = default)
    {
        var batch = await _store.ClaimPendingAsync(batchSize, ct);
        if (batch.Count == 0)
            return 0;

        try
        {
            await _recombee.SendInteractionsAsync(batch, ct);
            await _store.MarkSentAsync(batch, ct);
            _logger.LogDebug("Flushed {Count} interactions to Recombee", batch.Count);
            return batch.Count;
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex,
                "Flushing {Count} interactions to Recombee failed; returning them to the queue", batch.Count);
            await _store.ReturnToPendingAsync(batch, ct);
            throw;
        }
    }
}
