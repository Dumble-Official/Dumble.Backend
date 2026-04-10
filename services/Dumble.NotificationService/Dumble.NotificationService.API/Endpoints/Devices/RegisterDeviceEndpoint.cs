using FastEndpoints;
using MediatR;
using Dumble.NotificationService.Application.Features.Devices.Commands.RegisterDevice;
using Dumble.NotificationService.Contracts.Devices;

namespace Dumble.NotificationService.API.Endpoints.Devices;

public class RegisterDeviceEndpoint : Endpoint<RegisterDeviceRequest>
{
    private readonly IMediator _mediator;

    public RegisterDeviceEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Post("/api/notifications/devices");
        Claims("userId");
    }

    public override async Task HandleAsync(RegisterDeviceRequest req, CancellationToken ct)
    {
        var userId = User.FindFirst("userId")!.Value;
        await _mediator.Send(new RegisterDeviceCommand(userId, req.Token, req.Platform), ct);
        await SendNoContentAsync(ct);
    }
}
