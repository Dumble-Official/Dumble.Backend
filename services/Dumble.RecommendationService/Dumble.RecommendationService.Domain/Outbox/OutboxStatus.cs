namespace Dumble.RecommendationService.Domain.Outbox;

/// <summary>
/// Lifecycle of a buffered interaction. Rows are created <see cref="Pending"/>, atomically
/// claimed into <see cref="Processing"/> by a flush worker (so concurrent workers never grab
/// the same rows), then flipped to <see cref="Sent"/> once Recombee acknowledges the batch and
/// reaped. A failed flush returns the row to <see cref="Pending"/> for retry.
/// </summary>
public enum OutboxStatus
{
    Pending,
    Processing,
    Sent
}
