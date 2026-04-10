using FastEndpoints;
using MediatR;
using Dumble.SocialService.Application.Features.Feed.Queries.GetExploreFeed;
using Dumble.SocialService.Contracts.Common;
using Dumble.SocialService.Contracts.Feed;

namespace Dumble.SocialService.API.Endpoints.Feed;

public class GetExploreFeedEndpoint : EndpointWithoutRequest<CursorPagedResponse<FeedPostResponse>>
{
    private readonly IMediator _mediator;

    public GetExploreFeedEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/feed/explore");
        AllowAnonymous();
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var cursor = Query<string?>("cursor");
        var limit = Query<int?>("limit") ?? 20;

        var result = await _mediator.Send(new GetExploreFeedQuery(cursor, limit), ct);
        await SendAsync(result, cancellation: ct);
    }
}
