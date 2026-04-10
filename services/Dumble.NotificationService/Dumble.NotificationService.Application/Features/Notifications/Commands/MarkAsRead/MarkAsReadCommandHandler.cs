using MediatR;
using Dumble.NotificationService.Application.Contracts;

namespace Dumble.NotificationService.Application.Features.Notifications.Commands.MarkAsRead;

public class MarkAsReadCommandHandler : IRequestHandler<MarkAsReadCommand>
{
    private readonly INotificationRepository _repository;

    public MarkAsReadCommandHandler(INotificationRepository repository)
    {
        _repository = repository;
    }

    public async Task Handle(MarkAsReadCommand request, CancellationToken ct)
    {
        await _repository.MarkAsReadAsync(request.NotificationId, ct);
    }
}
