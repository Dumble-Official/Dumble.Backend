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

    public List<RecombeeItemUpsert> UpsertedItems { get; } = new();
    public int UpsertBatchCalls { get; private set; }

    public Task UpsertItemsAsync(IReadOnlyList<RecombeeItemUpsert> items, CancellationToken ct = default)
    {
        UpsertBatchCalls++;
        UpsertedItems.AddRange(items);
        return Task.CompletedTask;
    }

    public List<string> DeletedItems { get; } = new();

    public Task DeleteItemAsync(string itemId, CancellationToken ct = default)
    {
        DeletedItems.Add(itemId);
        return Task.CompletedTask;
    }

    public List<string> DeletedUsers { get; } = new();

    public Task DeleteUserAsync(string userId, CancellationToken ct = default)
    {
        DeletedUsers.Add(userId);
        return Task.CompletedTask;
    }

    public List<string> ItemIds { get; set; } = new();

    public Task<IReadOnlyList<string>> ListItemIdsAsync(CancellationToken ct = default)
        => Task.FromResult<IReadOnlyList<string>>(ItemIds.ToList());

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

    public IReadOnlyList<string> RecommendUsersResult { get; set; } = Array.Empty<string>();
    public Exception? ThrowOnRecommendUsers { get; set; }

    public Task<IReadOnlyList<string>> RecommendUsersToUserAsync(string userId, int count, CancellationToken ct = default)
    {
        if (ThrowOnRecommendUsers is not null)
            throw ThrowOnRecommendUsers;
        return Task.FromResult(RecommendUsersResult);
    }
}
