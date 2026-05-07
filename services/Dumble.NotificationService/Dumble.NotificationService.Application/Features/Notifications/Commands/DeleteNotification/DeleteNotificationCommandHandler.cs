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
        var notification = await _repository.GetByIdAsync(request.NotificationId, ct)
            ?? throw new KeyNotFoundException($"Notification '{request.NotificationId}' not found");

        if (notification.RecipientId != request.CallerId)
            throw new UnauthorizedAccessException("You can only delete your own notifications");

        await _repository.DeleteAsync(request.NotificationId, ct);
    }
}
