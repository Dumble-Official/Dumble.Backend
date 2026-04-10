using FastEndpoints;
using MediatR;
using Dumble.ChatService.Application.Features.Presence.Queries.GetBatchPresence;
using Dumble.ChatService.Contracts.Presence;

namespace Dumble.ChatService.API.Endpoints.Presence;

public class BatchPresenceEndpoint : Endpoint<BatchPresenceRequest, BatchPresenceResponse>
{
    private readonly IMediator _mediator;

    public BatchPresenceEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Post("/api/chat/presence/batch");
        Claims("userId");
    }

    public override async Task HandleAsync(BatchPresenceRequest req, CancellationToken ct)
    {
        var result = await _mediator.Send(new GetBatchPresenceQuery(req.UserIds), ct);
        await SendAsync(result, cancellation: ct);
    }
}
