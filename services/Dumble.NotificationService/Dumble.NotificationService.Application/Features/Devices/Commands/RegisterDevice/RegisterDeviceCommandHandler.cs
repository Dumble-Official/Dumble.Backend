using MediatR;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;

namespace Dumble.NotificationService.Application.Features.Devices.Commands.RegisterDevice;

public class RegisterDeviceCommandHandler : IRequestHandler<RegisterDeviceCommand>
{
    private readonly IDeviceTokenRepository _repository;

    public RegisterDeviceCommandHandler(IDeviceTokenRepository repository)
    {
        _repository = repository;
    }

    public async Task Handle(RegisterDeviceCommand request, CancellationToken ct)
    {
        var deviceToken = new DeviceToken
        {
            UserId = request.UserId,
            Token = request.Token,
            Platform = request.Platform,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        await _repository.UpsertAsync(deviceToken, ct);
    }
}
