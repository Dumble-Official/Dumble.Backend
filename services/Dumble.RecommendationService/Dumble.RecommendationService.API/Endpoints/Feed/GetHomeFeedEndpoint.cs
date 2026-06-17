using Dumble.RecommendationService.Application.Features.Feed.GetHomeFeed;
using Dumble.RecommendationService.Contracts.Common;
using Dumble.RecommendationService.Contracts.Feed;
using FastEndpoints;
using MediatR;

namespace Dumble.RecommendationService.API.Endpoints.Feed;

/// <summary>
/// Home feed: Recombee-ranked posts from the people the caller follows. Same engine as explore,
/// filtered to the followee set, so all feed ranking lives in one place.
/// </summary>
public sealed class GetHomeFeedEndpoint : EndpointWithoutRequest<CursorPagedResponse<FeedPostResponse>>
{
    private readonly ISender _sender;

    public GetHomeFeedEndpoint(ISender sender) => _sender = sender;

    public override void Configure()
    {
        Get("/api/feed/home");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var userId = User.FindFirst("userId")!.Value;
        var cursor = Query<string?>("cursor", isRequired: false);
        var limit = Math.Clamp(Query<int?>("limit", isRequired: false) ?? 20, 1, 100);

        var result = await _sender.Send(new GetHomeFeedQuery(userId, cursor, limit), ct);
        await SendAsync(result, cancellation: ct);
    }
}
