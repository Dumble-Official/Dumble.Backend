namespace Dumble.RecommendationService.Domain.Outbox;

/// <summary>
/// Lifecycle of a buffered interaction. Rows are created <see cref="Pending"/>,
/// flipped to <see cref="Sent"/> once Recombee acknowledges the batch, then reaped.
/// </summary>
public enum OutboxStatus
{
    Pending,
    Sent
}
