using MediatR;

namespace Dumble.NotificationService.Application.Features.Devices.Commands.RegisterDevice;

public record RegisterDeviceCommand(string UserId, string Token, string Platform) : IRequest;
