using FastEndpoints;
using MediatR;
using Dumble.NotificationService.Application.Features.Notifications.Commands.MarkAllAsRead;

namespace Dumble.NotificationService.API.Endpoints.Notifications;

public class MarkAllAsReadEndpoint : EndpointWithoutRequest
{
    private readonly IMediator _mediator;

    public MarkAllAsReadEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Put("/api/notifications/read-all");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var userId = User.FindFirst("userId")!.Value;
        await _mediator.Send(new MarkAllAsReadCommand(userId), ct);
        await SendNoContentAsync(ct);
    }
}
