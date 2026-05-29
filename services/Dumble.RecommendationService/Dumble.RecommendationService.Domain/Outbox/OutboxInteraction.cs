namespace Dumble.RecommendationService.Domain.Outbox;

/// <summary>
/// A durably-buffered interaction awaiting flush to Recombee (design D10, D14).
/// Both ingestion channels land here: client-only signals (no <see cref="SourceEventId"/>)
/// and domain events (<see cref="SourceEventId"/> = the integration event id, which the
/// unique index uses to make Channel-2 consumption idempotent under redelivery).
/// Timestamps are supplied by the caller rather than read from the clock so the
/// type stays deterministic and unit-testable.
/// </summary>
public sealed class OutboxInteraction
{
    // EF materialisation ctor.
    private OutboxInteraction() { }

    public long Id { get; private set; }
    public string UserId { get; private set; } = null!;
    public string ItemId { get; private set; } = null!;
    public OutboxOperation Operation { get; private set; }

    /// <summary>Set only for <see cref="OutboxOperation.AddRating"/>; the Recombee rating in [-1, 1].</summary>
    public double? RatingValue { get; private set; }

    /// <summary>Set only for dwell signals on <see cref="OutboxOperation.AddDetailView"/>.</summary>
    public int? DurationSeconds { get; private set; }

    /// <summary>When the interaction actually happened — forwarded as the Recombee interaction timestamp.</summary>
    public DateTimeOffset OccurredAt { get; private set; }

    /// <summary>Integration-event id for Channel-2 idempotency; null for client-only signals.</summary>
    public string? SourceEventId { get; private set; }

    public OutboxStatus Status { get; private set; }
    public int Attempts { get; private set; }
    public DateTimeOffset CreatedAt { get; private set; }

    public static OutboxInteraction Create(
        string userId,
        string itemId,
        OutboxOperation operation,
        DateTimeOffset occurredAt,
        DateTimeOffset createdAt,
        double? ratingValue = null,
        int? durationSeconds = null,
        string? sourceEventId = null)
    {
        if (string.IsNullOrWhiteSpace(userId))
            throw new ArgumentException("userId is required", nameof(userId));
        if (string.IsNullOrWhiteSpace(itemId))
            throw new ArgumentException("itemId is required", nameof(itemId));
        if (operation == OutboxOperation.AddRating && ratingValue is null)
            throw new ArgumentException("AddRating requires a ratingValue", nameof(ratingValue));

        return new OutboxInteraction
        {
            UserId = userId,
            ItemId = itemId,
            Operation = operation,
            RatingValue = ratingValue,
            DurationSeconds = durationSeconds,
            OccurredAt = occurredAt,
            SourceEventId = string.IsNullOrWhiteSpace(sourceEventId) ? null : sourceEventId,
            Status = OutboxStatus.Pending,
            Attempts = 0,
            CreatedAt = createdAt
        };
    }

    /// <summary>Mark as flushed after Recombee acknowledges the batch.</summary>
    public void MarkSent() => Status = OutboxStatus.Sent;

    /// <summary>Record a failed flush attempt so retries/backoff can be reasoned about.</summary>
    public void RecordFailedAttempt() => Attempts++;
}
