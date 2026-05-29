using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Domain.Outbox;
using MediatR;

namespace Dumble.RecommendationService.Application.Features.Interactions.RecordInteraction;

public sealed class RecordInteractionCommandHandler
    : IRequestHandler<RecordInteractionCommand, RecordInteractionResult>
{
    private readonly IInteractionOutboxWriter _writer;
    private readonly TimeProvider _clock;

    public RecordInteractionCommandHandler(IInteractionOutboxWriter writer, TimeProvider clock)
    {
        _writer = writer;
        _clock = clock;
    }

    public async Task<RecordInteractionResult> Handle(RecordInteractionCommand request, CancellationToken ct)
    {
        var mapping = InteractionSignalMapper.Map(request.Signal);

        // Duration is only meaningful for detail views (dwell time); ignore it otherwise.
        var duration = mapping.Operation == OutboxOperation.AddDetailView ? request.DurationSeconds : null;

        var interaction = OutboxInteraction.Create(
            userId: request.UserId,
            itemId: request.ItemId,
            operation: mapping.Operation,
            occurredAt: request.OccurredAt,
            createdAt: _clock.GetUtcNow(),
            ratingValue: mapping.RatingValue,
            durationSeconds: duration,
            sourceEventId: request.SourceEventId);

        var buffered = await _writer.AddAsync(interaction, ct);
        return new RecordInteractionResult(buffered);
    }
}
