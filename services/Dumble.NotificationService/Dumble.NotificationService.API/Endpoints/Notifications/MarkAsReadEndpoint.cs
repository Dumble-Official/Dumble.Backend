using FastEndpoints;
using MediatR;
using Dumble.NotificationService.Application.Features.Notifications.Commands.MarkAsRead;

namespace Dumble.NotificationService.API.Endpoints.Notifications;

public class MarkAsReadEndpoint : EndpointWithoutRequest
{
    private readonly IMediator _mediator;

    public MarkAsReadEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Put("/api/notifications/{id}/read");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var id = Route<string>("id")!;
        var userId = User.FindFirst("userId")!.Value;
        await _mediator.Send(new MarkAsReadCommand(id, userId), ct);
        await SendNoContentAsync(ct);
    }
}
