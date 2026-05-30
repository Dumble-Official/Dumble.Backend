using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Domain.Outbox;

namespace Dumble.RecommendationService.Tests.TestDoubles;

internal sealed class FakeOutboxFlushStore : IOutboxFlushStore
{
    private readonly Queue<OutboxInteraction> _pending = new();

    public List<OutboxInteraction> Sent { get; } = new();
    public List<OutboxInteraction> ReturnedToPending { get; } = new();

    public void Seed(params OutboxInteraction[] items)
    {
        foreach (var item in items)
            _pending.Enqueue(item);
    }

    public Task<IReadOnlyList<OutboxInteraction>> ClaimPendingAsync(int batchSize, CancellationToken ct = default)
    {
        var claimed = new List<OutboxInteraction>();
        while (claimed.Count < batchSize && _pending.Count > 0)
            claimed.Add(_pending.Dequeue());
        return Task.FromResult<IReadOnlyList<OutboxInteraction>>(claimed);
    }

    public Task MarkSentAsync(IReadOnlyList<OutboxInteraction> sent, CancellationToken ct = default)
    {
        Sent.AddRange(sent);
        return Task.CompletedTask;
    }

    public Task ReturnToPendingAsync(IReadOnlyList<OutboxInteraction> failed, CancellationToken ct = default)
    {
        ReturnedToPending.AddRange(failed);
        return Task.CompletedTask;
    }
}
