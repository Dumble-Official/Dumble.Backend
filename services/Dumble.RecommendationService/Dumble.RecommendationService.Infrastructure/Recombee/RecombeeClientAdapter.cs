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
        // Send only the properties present, so an update touches just what changed.
        var values = new Dictionary<string, object>();
        if (item.Author is not null) values["author"] = item.Author;
        if (item.AuthorType is not null) values["authorType"] = item.AuthorType;
        if (item.GymId is not null) values["gymId"] = item.GymId;
        if (item.Hashtags is not null) values["hashtags"] = item.Hashtags.ToArray();
        if (item.CreatedAt is not null) values["createdAt"] = item.CreatedAt.Value.UtcDateTime;

        if (values.Count == 0)
            return;

        await _client.SendAsync(new SetItemValues(item.ItemId, values, cascadeCreate: true));
    }

    public async Task DeleteItemAsync(string itemId, CancellationToken ct = default)
    {
        await _client.SendAsync(new DeleteItem(itemId));
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
