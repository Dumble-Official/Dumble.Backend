using FastEndpoints;
using MediatR;
using Dumble.SocialService.Application.Features.Behavior.Commands.TrackBehavior;
using Dumble.SocialService.Contracts.Behavior;

namespace Dumble.SocialService.API.Endpoints.Behavior;

public class TrackBehaviorEndpoint : Endpoint<TrackBehaviorRequest>
{
    private readonly IMediator _mediator;

    public TrackBehaviorEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Post("/api/feed/behavior");
        Claims("userId");
    }

    public override async Task HandleAsync(TrackBehaviorRequest req, CancellationToken ct)
    {
        var userId = User.FindFirst("userId")!.Value;
        await _mediator.Send(new TrackBehaviorCommand(userId, req.PostId, req.EventType, req.EventData), ct);
        await SendNoContentAsync(ct);
    }
}
