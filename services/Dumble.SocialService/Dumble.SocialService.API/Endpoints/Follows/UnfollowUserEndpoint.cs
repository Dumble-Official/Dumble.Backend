using FastEndpoints;
using MediatR;
using Dumble.SocialService.Application.Features.Follows.Commands.UnfollowUser;

namespace Dumble.SocialService.API.Endpoints.Follows;

public class UnfollowUserEndpoint : EndpointWithoutRequest
{
    private readonly IMediator _mediator;

    public UnfollowUserEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Delete("/api/social/follow/{userId}");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var followerId = User.FindFirst("userId")!.Value;
        var followeeId = Route<string>("userId")!;

        await _mediator.Send(new UnfollowUserCommand(followerId, followeeId), ct);
        await SendNoContentAsync(ct);
    }
}
