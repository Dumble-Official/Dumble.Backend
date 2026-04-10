using MediatR;
using Dumble.NotificationService.Application.Contracts;

namespace Dumble.NotificationService.Application.Features.Notifications.Commands.DeleteNotification;

public class DeleteNotificationCommandHandler : IRequestHandler<DeleteNotificationCommand>
{
    private readonly INotificationRepository _repository;

    public DeleteNotificationCommandHandler(INotificationRepository repository)
    {
        _repository = repository;
    }

    public async Task Handle(DeleteNotificationCommand request, CancellationToken ct)
    {
        await _repository.DeleteAsync(request.NotificationId, ct);
    }
}
