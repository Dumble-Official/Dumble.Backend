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

    public Task UpsertItemAsync(RecombeeItemUpsert item, CancellationToken ct = default) => Task.CompletedTask;

    public Task DeleteItemAsync(string itemId, CancellationToken ct = default) => Task.CompletedTask;

    public Task EnsureSchemaAsync(CancellationToken ct = default) => Task.CompletedTask;

    public IReadOnlyList<string> RecommendResult { get; set; } = Array.Empty<string>();
    public Exception? ThrowOnRecommend { get; set; }
    public int RecommendCalls { get; private set; }

    public Task<IReadOnlyList<string>> RecommendItemsToUserAsync(string userId, int count, CancellationToken ct = default)
    {
        RecommendCalls++;
        if (ThrowOnRecommend is not null)
            throw ThrowOnRecommend;
        return Task.FromResult(RecommendResult);
    }
}
