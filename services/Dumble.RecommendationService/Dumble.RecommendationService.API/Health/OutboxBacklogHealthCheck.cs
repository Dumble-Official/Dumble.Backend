using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Application.Outbox;
using Microsoft.Extensions.Diagnostics.HealthChecks;

namespace Dumble.RecommendationService.API.Health;

/// <summary>
/// Readiness check surfacing the interaction-outbox backlog (D18). Reports Degraded — not
/// Unhealthy — when the oldest pending row exceeds the age threshold: the instance stays in
/// rotation (behaviour still buffers fine) but monitoring can alert that the flush to Recombee
/// has stalled, which is otherwise invisible to users.
/// </summary>
internal sealed class OutboxBacklogHealthCheck : IHealthCheck
{
    private static readonly TimeSpan MaxPendingAge = TimeSpan.FromMinutes(5);

    private readonly IOutboxFlushStore _store;
    private readonly TimeProvider _clock;

    public OutboxBacklogHealthCheck(IOutboxFlushStore store, TimeProvider clock)
    {
        _store = store;
        _clock = clock;
    }

    public async Task<HealthCheckResult> CheckHealthAsync(HealthCheckContext context, CancellationToken cancellationToken = default)
    {
        var backlog = await _store.GetBacklogAsync(cancellationToken);
        var assessment = OutboxBacklogEvaluator.Evaluate(backlog, _clock.GetUtcNow(), MaxPendingAge);

        var data = new Dictionary<string, object> { ["pendingCount"] = backlog.PendingCount };
        return assessment.Health == OutboxHealth.Degraded
            ? HealthCheckResult.Degraded(assessment.Description, data: data)
            : HealthCheckResult.Healthy(assessment.Description, data);
    }
}
