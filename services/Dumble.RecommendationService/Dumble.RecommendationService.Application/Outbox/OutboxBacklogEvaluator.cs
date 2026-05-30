using Dumble.RecommendationService.Application.Contracts;

namespace Dumble.RecommendationService.Application.Outbox;

public enum OutboxHealth
{
    Healthy,
    Degraded
}

public sealed record OutboxBacklogAssessment(OutboxHealth Health, string Description);

/// <summary>
/// Turns an outbox backlog snapshot into a health verdict. The signal that matters (D18) is the
/// AGE of the oldest pending row: if interactions stop reaching Recombee the user sees nothing
/// wrong, but quality silently rots — so a stale backlog is the canary. Pure, so it is unit-tested.
/// </summary>
public static class OutboxBacklogEvaluator
{
    public static OutboxBacklogAssessment Evaluate(OutboxBacklog backlog, DateTimeOffset now, TimeSpan maxPendingAge)
    {
        if (backlog.PendingCount == 0 || backlog.OldestPendingAt is null)
            return new OutboxBacklogAssessment(OutboxHealth.Healthy, "No pending interactions");

        var age = now - backlog.OldestPendingAt.Value;
        if (age >= maxPendingAge)
            return new OutboxBacklogAssessment(
                OutboxHealth.Degraded,
                $"Oldest pending interaction is {age.TotalMinutes:F0}m old ({backlog.PendingCount} pending) — flush to Recombee may be stalled");

        return new OutboxBacklogAssessment(
            OutboxHealth.Healthy,
            $"{backlog.PendingCount} pending, oldest {age.TotalSeconds:F0}s");
    }
}
