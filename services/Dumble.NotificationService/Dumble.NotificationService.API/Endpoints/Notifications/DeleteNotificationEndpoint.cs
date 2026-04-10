using FastEndpoints;
using MediatR;
using Dumble.NotificationService.Application.Features.Notifications.Commands.DeleteNotification;

namespace Dumble.NotificationService.API.Endpoints.Notifications;

public class DeleteNotificationEndpoint : EndpointWithoutRequest
{
    private readonly IMediator _mediator;

    public DeleteNotificationEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Delete("/api/notifications/{id}");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var id = Route<string>("id")!;
        await _mediator.Send(new DeleteNotificationCommand(id), ct);
        await SendNoContentAsync(ct);
    }
}
