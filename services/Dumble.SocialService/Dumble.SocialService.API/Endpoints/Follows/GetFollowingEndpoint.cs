using FastEndpoints;
using MediatR;
using Dumble.SocialService.Application.Features.Follows.Queries.GetFollowing;
using Dumble.SocialService.Contracts.Common;
using Dumble.SocialService.Contracts.Follows;

namespace Dumble.SocialService.API.Endpoints.Follows;

public class GetFollowingEndpoint : EndpointWithoutRequest<CursorPagedResponse<FollowResponse>>
{
    private readonly IMediator _mediator;

    public GetFollowingEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/social/following/{userId}");
        AllowAnonymous();
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var userId = Route<string>("userId")!;
        var cursor = Query<string?>("cursor");
        var limit = Query<int?>("limit") ?? 20;

        var result = await _mediator.Send(new GetFollowingQuery(userId, cursor, limit), ct);
        await SendAsync(result, cancellation: ct);
    }
}
