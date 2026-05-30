using Dumble.RecommendationService.Application.Features.Feed.GetExploreFeed;
using Dumble.RecommendationService.Contracts.Common;
using Dumble.RecommendationService.Contracts.Feed;
using FastEndpoints;
using MediatR;

namespace Dumble.RecommendationService.API.Endpoints.Feed;

/// <summary>
/// Personalized explore feed. Relocated from SocialService at the same path; now personalized
/// (reads the userId claim) since Recombee recommends per user. Falls back to recent posts
/// when Recombee has nothing (outage or cold start).
/// </summary>
public sealed class GetExploreFeedEndpoint : EndpointWithoutRequest<CursorPagedResponse<FeedPostResponse>>
{
    private readonly ISender _sender;

    public GetExploreFeedEndpoint(ISender sender) => _sender = sender;

    public override void Configure()
    {
        Get("/api/feed/explore");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var userId = User.FindFirst("userId")!.Value;
        var cursor = Query<string?>("cursor", isRequired: false);
        var limit = Math.Clamp(Query<int?>("limit", isRequired: false) ?? 20, 1, 100);

        var result = await _sender.Send(new GetExploreFeedQuery(userId, cursor, limit), ct);
        await SendAsync(result, cancellation: ct);
    }
}
