using MediatR;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Contracts.Devices;

namespace Dumble.NotificationService.Application.Features.Devices.Queries.GetDevices;

public class GetDevicesQueryHandler : IRequestHandler<GetDevicesQuery, List<DeviceResponse>>
{
    private readonly IDeviceTokenRepository _deviceTokenRepository;

    public GetDevicesQueryHandler(IDeviceTokenRepository deviceTokenRepository)
    {
        _deviceTokenRepository = deviceTokenRepository;
    }

    public async Task<List<DeviceResponse>> Handle(GetDevicesQuery request, CancellationToken ct)
    {
        var devices = await _deviceTokenRepository.GetByUserIdAsync(request.UserId, ct);
        return devices
            .Select(d => new DeviceResponse(d.Id, d.Token, d.Platform, d.CreatedAt))
            .ToList();
    }
}
