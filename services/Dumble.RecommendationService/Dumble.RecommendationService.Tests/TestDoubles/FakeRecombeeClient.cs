using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Domain.Outbox;

namespace Dumble.RecommendationService.Tests.TestDoubles;

internal sealed class FakeRecombeeClient : IRecombeeClient
{
    public int SendCalls { get; private set; }
    public List<OutboxInteraction> LastSent { get; private set; } = new();
    public Exception? ThrowOnSend { get; set; }

    public Task SendInteractionsAsync(IReadOnlyList<OutboxInteraction> interactions, CancellationToken ct = default)
    {
        SendCalls++;
        LastSent = interactions.ToList();
        if (ThrowOnSend is not null)
            throw ThrowOnSend;
        return Task.CompletedTask;
    }
}
