using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Domain.Outbox;
using Microsoft.EntityFrameworkCore;

namespace Dumble.RecommendationService.Infrastructure.Persistence;

public sealed class EfOutboxFlushStore : IOutboxFlushStore
{
    private readonly RecommendationDbContext _db;

    public EfOutboxFlushStore(RecommendationDbContext db) => _db = db;

    public async Task<IReadOnlyList<OutboxInteraction>> ClaimPendingAsync(int batchSize, CancellationToken ct = default)
    {
        // Atomically claim the oldest pending rows: the inner SELECT locks them with
        // FOR UPDATE SKIP LOCKED (so a second worker skips rows already being claimed),
        // the UPDATE flips them to Processing, and RETURNING * hands back the claimed
        // entities (tracked) to send. The lock is released as soon as this statement
        // commits, so no DB lock is held across the Recombee HTTP call.
        const string sql = @"
UPDATE interaction_outbox
SET status = 'Processing'
WHERE id IN (
    SELECT id FROM interaction_outbox
    WHERE status = 'Pending'
    ORDER BY id
    LIMIT {0}
    FOR UPDATE SKIP LOCKED
)
RETURNING *;";

        return await _db.OutboxInteractions
            .FromSqlRaw(sql, batchSize)
            .ToListAsync(ct);
    }

    public async Task MarkSentAsync(IReadOnlyList<OutboxInteraction> sent, CancellationToken ct = default)
    {
        foreach (var interaction in sent)
            interaction.MarkSent();

        await _db.SaveChangesAsync(ct);
    }

    public async Task ReturnToPendingAsync(IReadOnlyList<OutboxInteraction> failed, CancellationToken ct = default)
    {
        foreach (var interaction in failed)
            interaction.MarkPendingRetry();

        await _db.SaveChangesAsync(ct);
    }

    public async Task<OutboxBacklog> GetBacklogAsync(CancellationToken ct = default)
    {
        var pending = _db.OutboxInteractions.Where(x => x.Status == OutboxStatus.Pending);
        var count = await pending.CountAsync(ct);
        DateTimeOffset? oldest = count == 0
            ? null
            : await pending.MinAsync(x => (DateTimeOffset?)x.CreatedAt, ct);
        return new OutboxBacklog(count, oldest);
    }
}
