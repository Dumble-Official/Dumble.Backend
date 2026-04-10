using MediatR;
using Dumble.NotificationService.Application.Contracts;

namespace Dumble.NotificationService.Application.Features.Devices.Commands.UnregisterDevice;

public class UnregisterDeviceCommandHandler : IRequestHandler<UnregisterDeviceCommand>
{
    private readonly IDeviceTokenRepository _repository;

    public UnregisterDeviceCommandHandler(IDeviceTokenRepository repository)
    {
        _repository = repository;
    }

    public async Task Handle(UnregisterDeviceCommand request, CancellationToken ct)
    {
        await _repository.DeleteByTokenAsync(request.Token, ct);
    }
}
