using FastEndpoints;
using MediatR;
using Dumble.SocialService.Application.Features.Feed.Queries.GetFeed;
using Dumble.SocialService.Contracts.Common;
using Dumble.SocialService.Contracts.Feed;

namespace Dumble.SocialService.API.Endpoints.Feed;

public class GetFeedEndpoint : EndpointWithoutRequest<CursorPagedResponse<FeedPostResponse>>
{
    private readonly IMediator _mediator;

    public GetFeedEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/feed");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var userId = User.FindFirst("userId")!.Value;
        var cursor = Query<string?>("cursor");
        var limit = Query<int?>("limit") ?? 20;

        var result = await _mediator.Send(new GetFeedQuery(userId, cursor, limit), ct);
        await SendAsync(result, cancellation: ct);
    }
}
