using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Domain.Outbox;
using Microsoft.Extensions.Options;
using Recombee.ApiClient;
using Recombee.ApiClient.ApiRequests;
using Recombee.ApiClient.Util;

namespace Dumble.RecommendationService.Infrastructure.Recombee;

/// <summary>
/// The only place the Recombee SDK is touched. Translates buffered outbox operations into
/// Recombee requests and sends them as a single batch. cascadeCreate is set on every
/// interaction so a user/item referenced before its catalog event still gets created.
/// </summary>
public sealed class RecombeeClientAdapter : IRecombeeClient
{
    private readonly RecombeeClient _client;

    public RecombeeClientAdapter(IOptions<RecombeeOptions> options)
    {
        var o = options.Value;
        _client = new RecombeeClient(o.DatabaseId, o.PrivateToken, region: ParseRegion(o.Region));
    }

    public async Task SendInteractionsAsync(IReadOnlyList<OutboxInteraction> interactions, CancellationToken ct = default)
    {
        if (interactions.Count == 0)
            return;

        var requests = new List<Request>(interactions.Count);
        foreach (var interaction in interactions)
            requests.Add(ToRequest(interaction));

        await _client.SendAsync(new Batch(requests));
    }

    public async Task UpsertItemAsync(RecombeeItemUpsert item, CancellationToken ct = default)
    {
        var values = ToValues(item);
        if (values.Count == 0)
            return;

        await _client.SendAsync(new SetItemValues(item.ItemId, values, cascadeCreate: true));
    }

    public async Task UpsertItemsAsync(IReadOnlyList<RecombeeItemUpsert> items, CancellationToken ct = default)
    {
        if (items.Count == 0)
            return;

        var requests = new List<Request>(items.Count);
        foreach (var item in items)
        {
            var values = ToValues(item);
            if (values.Count == 0)
                continue;
            requests.Add(new SetItemValues(item.ItemId, values, cascadeCreate: true));
        }

        if (requests.Count == 0)
            return;

        await _client.SendAsync(new Batch(requests));
    }

    // Send only the properties present, so an update touches just what changed.
    private static Dictionary<string, object> ToValues(RecombeeItemUpsert item)
    {
        var values = new Dictionary<string, object>();
        if (item.Author is not null) values["author"] = item.Author;
        if (item.AuthorType is not null) values["authorType"] = item.AuthorType;
        if (item.GymId is not null) values["gymId"] = item.GymId;
        if (item.Hashtags is not null) values["hashtags"] = item.Hashtags.ToArray();
        if (item.CreatedAt is not null) values["createdAt"] = item.CreatedAt.Value.UtcDateTime;
        return values;
    }

    public async Task DeleteItemAsync(string itemId, CancellationToken ct = default)
    {
        try
        {
            await _client.SendAsync(new DeleteItem(itemId));
        }
        catch (ResponseException ex) when (IsNotFound(ex))
        {
            // Idempotent: an item that was never in Recombee (or already removed) is already gone.
        }
    }

    public async Task DeleteUserAsync(string userId, CancellationToken ct = default)
    {
        // Recombee removes the user along with all of their interactions.
        try
        {
            await _client.SendAsync(new DeleteUser(userId));
        }
        catch (ResponseException ex) when (IsNotFound(ex))
        {
            // Idempotent: a user who never interacted was never created in Recombee, so there is
            // nothing to forget. RTBF deletes must not fail on an already-absent user.
        }
    }

    // Recombee answers a delete of a missing user/item with 404 + a "... does not exist!" message.
    private static bool IsNotFound(ResponseException ex) =>
        ex.Message.Contains("does not exist", StringComparison.OrdinalIgnoreCase);

    public async Task<IReadOnlyList<string>> ListItemIdsAsync(CancellationToken ct = default)
    {
        // Page through the whole catalog; ids only (no properties), so each page stays light.
        const int pageSize = 1000;
        var ids = new List<string>();
        long offset = 0;

        while (!ct.IsCancellationRequested)
        {
            var page = (await _client.SendAsync(new ListItems(count: pageSize, offset: offset))).ToList();
            if (page.Count == 0)
                break;

            ids.AddRange(page.Select(i => i.ItemId));
            if (page.Count < pageSize)
                break;

            offset += page.Count;
        }

        return ids;
    }

    public async Task<IReadOnlyList<string>> RecommendItemsToUserAsync(string userId, int count, CancellationToken ct = default)
    {
        var response = await _client.SendAsync(new RecommendItemsToUser(userId, count));
        return response.Recomms.Select(r => r.Id).ToList();
    }

    public async Task<IReadOnlyList<string>> RecommendUsersToUserAsync(string userId, int count, CancellationToken ct = default)
    {
        var response = await _client.SendAsync(new RecommendUsersToUser(userId, count));
        return response.Recomms.Select(r => r.Id).ToList();
    }

    public async Task EnsureSchemaAsync(CancellationToken ct = default)
    {
        foreach (var (name, type) in ItemProperties)
        {
            try
            {
                await _client.SendAsync(new AddItemProperty(name, type));
            }
            catch (ResponseException)
            {
                // Property already exists — AddItemProperty is not idempotent, so this is expected.
            }
        }
    }

    // The item profile Recombee ranks on (design D11).
    private static readonly (string Name, string Type)[] ItemProperties =
    {
        ("author", "string"),
        ("authorType", "string"),
        ("gymId", "string"),
        ("hashtags", "set"),
        ("createdAt", "timestamp")
    };

    private static Request ToRequest(OutboxInteraction i)
    {
        var timestamp = i.OccurredAt.UtcDateTime;
        return i.Operation switch
        {
            OutboxOperation.AddDetailView => new AddDetailView(i.UserId, i.ItemId,
                timestamp: timestamp, cascadeCreate: true, duration: i.DurationSeconds),
            OutboxOperation.AddRating => new AddRating(i.UserId, i.ItemId, i.RatingValue!.Value,
                timestamp: timestamp, cascadeCreate: true),
            OutboxOperation.AddBookmark => new AddBookmark(i.UserId, i.ItemId,
                timestamp: timestamp, cascadeCreate: true),
            OutboxOperation.DeleteRating => new DeleteRating(i.UserId, i.ItemId,
                timestamp: timestamp),
            OutboxOperation.DeleteBookmark => new DeleteBookmark(i.UserId, i.ItemId,
                timestamp: timestamp),
            _ => throw new ArgumentOutOfRangeException(nameof(i), i.Operation, "Unmapped outbox operation")
        };
    }

    private static Region ParseRegion(string region) => region.Trim().ToLowerInvariant() switch
    {
        "ap-se" => Region.ApSe,
        "ca-east" => Region.CaEast,
        "eu-west" => Region.EuWest,
        "us-west" => Region.UsWest,
        _ => throw new InvalidOperationException(
            $"Unknown or missing Recombee region '{region}'. Set RECOMBEE_REGION (e.g. eu-west).")
    };
}
