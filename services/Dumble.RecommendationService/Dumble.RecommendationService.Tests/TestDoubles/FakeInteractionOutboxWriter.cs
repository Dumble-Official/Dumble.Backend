using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Domain.Outbox;

namespace Dumble.RecommendationService.Tests.TestDoubles;

/// <summary>Captures the interaction handed to it and returns a configurable buffered/deduped result.</summary>
internal sealed class FakeInteractionOutboxWriter : IInteractionOutboxWriter
{
    public OutboxInteraction? Captured { get; private set; }
    public int Calls { get; private set; }
    public bool NextResult { get; set; } = true;

    public Task<bool> AddAsync(OutboxInteraction interaction, CancellationToken ct = default)
    {
        Captured = interaction;
        Calls++;
        return Task.FromResult(NextResult);
    }
}
