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
