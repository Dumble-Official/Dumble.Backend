using MediatR;

namespace Dumble.NotificationService.Application.Features.Devices.Commands.UnregisterDevice;

public record UnregisterDeviceCommand(string Token, string CallerId) : IRequest;
