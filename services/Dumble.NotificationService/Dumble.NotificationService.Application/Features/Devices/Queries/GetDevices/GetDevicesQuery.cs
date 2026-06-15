using MediatR;
using Dumble.NotificationService.Contracts.Devices;

namespace Dumble.NotificationService.Application.Features.Devices.Queries.GetDevices;

public record GetDevicesQuery(string UserId) : IRequest<List<DeviceResponse>>;
