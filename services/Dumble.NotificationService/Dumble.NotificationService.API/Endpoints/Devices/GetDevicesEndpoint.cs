using FastEndpoints;
using MediatR;
using Dumble.NotificationService.Application.Features.Devices.Queries.GetDevices;
using Dumble.NotificationService.Contracts.Devices;

namespace Dumble.NotificationService.API.Endpoints.Devices;

public class GetDevicesEndpoint : EndpointWithoutRequest<List<DeviceResponse>>
{
    private readonly IMediator _mediator;

    public GetDevicesEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/notifications/devices");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var userId = User.FindFirst("userId")!.Value;
        var result = await _mediator.Send(new GetDevicesQuery(userId), ct);
        await SendAsync(result, cancellation: ct);
    }
}
