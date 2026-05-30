using Dumble.RecommendationService.Domain.Outbox;
using MediatR;

namespace Dumble.RecommendationService.Application.Features.Interactions.RecordInteraction;

/// <summary>
/// The single application entry point for recording an interaction, used by both
/// ingestion channels: client signals (HTTP) and domain-event consumers. The latter
/// pass <paramref name="SourceEventId"/> so redelivery is idempotent.
/// </summary>
public sealed record RecordInteractionCommand(
    string UserId,
    string ItemId,
    InteractionSignal Signal,
    DateTimeOffset OccurredAt,
    int? DurationSeconds = null,
    string? SourceEventId = null) : IRequest<RecordInteractionResult>;

/// <param name="Buffered"><c>true</c> if newly buffered; <c>false</c> if it was a duplicate event.</param>
public sealed record RecordInteractionResult(bool Buffered);
