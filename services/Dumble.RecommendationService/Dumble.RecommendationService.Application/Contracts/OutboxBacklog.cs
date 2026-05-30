namespace Dumble.RecommendationService.Application.Contracts;

/// <summary>Snapshot of the interaction outbox backlog, used for the flush-health canary (D18).</summary>
public sealed record OutboxBacklog(int PendingCount, DateTimeOffset? OldestPendingAt);
