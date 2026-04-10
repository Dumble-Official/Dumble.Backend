using FastEndpoints;
using MediatR;
using Dumble.SocialService.Application.Features.Follows.Queries.GetFollowCounts;
using Dumble.SocialService.Contracts.Follows;

namespace Dumble.SocialService.API.Endpoints.Follows;

public class GetFollowCountsEndpoint : EndpointWithoutRequest<FollowCountsResponse>
{
    private readonly IMediator _mediator;

    public GetFollowCountsEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/social/{userId}/counts");
        AllowAnonymous();
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var userId = Route<string>("userId")!;
        var result = await _mediator.Send(new GetFollowCountsQuery(userId), ct);
        await SendAsync(result, cancellation: ct);
    }
}
