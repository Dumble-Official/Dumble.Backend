using FastEndpoints;
using MediatR;
using Dumble.SocialService.Application.Features.Follows.Queries.GetFollowStatus;
using Dumble.SocialService.Contracts.Follows;

namespace Dumble.SocialService.API.Endpoints.Follows;

public class GetFollowStatusEndpoint : EndpointWithoutRequest<FollowStatusResponse>
{
    private readonly IMediator _mediator;

    public GetFollowStatusEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/social/follow/{userId}/status");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var followerId = User.FindFirst("userId")!.Value;
        var followeeId = Route<string>("userId")!;

        var result = await _mediator.Send(new GetFollowStatusQuery(followerId, followeeId), ct);
        await SendAsync(result, cancellation: ct);
    }
}
