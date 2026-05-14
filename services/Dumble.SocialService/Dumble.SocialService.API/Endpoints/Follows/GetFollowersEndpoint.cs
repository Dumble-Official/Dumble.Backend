using FastEndpoints;
using MediatR;
using Dumble.SocialService.Application.Features.Follows.Queries.GetFollowers;
using Dumble.SocialService.Contracts.Common;
using Dumble.SocialService.Contracts.Follows;

namespace Dumble.SocialService.API.Endpoints.Follows;

public class GetFollowersEndpoint : EndpointWithoutRequest<CursorPagedResponse<FollowResponse>>
{
    private readonly IMediator _mediator;

    public GetFollowersEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/social/followers/{userId}");
        AllowAnonymous();
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var userId = Route<string>("userId")!;
        var cursor = Query<string?>("cursor", isRequired: false);
        var limit = Math.Clamp(Query<int?>("limit", isRequired: false) ?? 20, 1, 100);

        var result = await _mediator.Send(new GetFollowersQuery(userId, cursor, limit), ct);
        await SendAsync(result, cancellation: ct);
    }
}
