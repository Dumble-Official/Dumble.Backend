using FastEndpoints;
using MediatR;
using Dumble.NotificationService.Application.Features.Devices.Commands.UnregisterDevice;

namespace Dumble.NotificationService.API.Endpoints.Devices;

public class UnregisterDeviceEndpoint : EndpointWithoutRequest
{
    private readonly IMediator _mediator;

    public UnregisterDeviceEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Delete("/api/notifications/devices/{token}");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var token = Route<string>("token")!;
        var userId = User.FindFirst("userId")!.Value;
        await _mediator.Send(new UnregisterDeviceCommand(token, userId), ct);
        await SendNoContentAsync(ct);
    }
}
