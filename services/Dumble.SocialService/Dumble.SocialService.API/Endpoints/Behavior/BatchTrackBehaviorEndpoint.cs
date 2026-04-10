using FastEndpoints;
using MediatR;
using Dumble.SocialService.Application.Features.Behavior.Commands.BatchTrackBehavior;
using Dumble.SocialService.Contracts.Behavior;

namespace Dumble.SocialService.API.Endpoints.Behavior;

public class BatchTrackBehaviorEndpoint : Endpoint<BatchTrackBehaviorRequest>
{
    private readonly IMediator _mediator;

    public BatchTrackBehaviorEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Post("/api/feed/behavior/batch");
        Claims("userId");
    }

    public override async Task HandleAsync(BatchTrackBehaviorRequest req, CancellationToken ct)
    {
        var userId = User.FindFirst("userId")!.Value;
        await _mediator.Send(new BatchTrackBehaviorCommand(userId, req.Events), ct);
        await SendNoContentAsync(ct);
    }
}
