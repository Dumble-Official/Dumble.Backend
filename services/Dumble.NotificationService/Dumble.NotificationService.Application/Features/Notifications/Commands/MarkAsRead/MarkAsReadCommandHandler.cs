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
        var notification = await _repository.GetByIdAsync(request.NotificationId, ct)
            ?? throw new KeyNotFoundException($"Notification '{request.NotificationId}' not found");

        if (notification.RecipientId != request.CallerId)
            throw new UnauthorizedAccessException("You can only mark your own notifications as read");

        await _repository.MarkAsReadAsync(request.NotificationId, ct);
    }
}
