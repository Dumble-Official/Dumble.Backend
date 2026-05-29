using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Domain.Outbox;
using Microsoft.EntityFrameworkCore;
using Npgsql;

namespace Dumble.RecommendationService.Infrastructure.Persistence;

public sealed class InteractionOutboxWriter : IInteractionOutboxWriter
{
    private readonly RecommendationDbContext _db;

    public InteractionOutboxWriter(RecommendationDbContext db)
    {
        _db = db;
    }

    public async Task<bool> AddAsync(OutboxInteraction interaction, CancellationToken ct = default)
    {
        _db.OutboxInteractions.Add(interaction);
        try
        {
            await _db.SaveChangesAsync(ct);
            return true;
        }
        catch (DbUpdateException ex) when (IsUniqueViolation(ex))
        {
            // A redelivered domain event carrying an already-buffered source event id.
            // Detach the rejected row and treat it as an idempotent no-op so the
            // consumer can safely ack the message.
            _db.Entry(interaction).State = EntityState.Detached;
            return false;
        }
    }

    private static bool IsUniqueViolation(DbUpdateException ex)
        => ex.InnerException is PostgresException { SqlState: PostgresErrorCodes.UniqueViolation };
}
