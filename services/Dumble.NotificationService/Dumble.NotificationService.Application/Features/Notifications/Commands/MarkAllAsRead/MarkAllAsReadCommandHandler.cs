using MediatR;
using Dumble.NotificationService.Application.Contracts;

namespace Dumble.NotificationService.Application.Features.Notifications.Commands.MarkAllAsRead;

public class MarkAllAsReadCommandHandler : IRequestHandler<MarkAllAsReadCommand>
{
    private readonly INotificationRepository _repository;

    public MarkAllAsReadCommandHandler(INotificationRepository repository)
    {
        _repository = repository;
    }

    public async Task Handle(MarkAllAsReadCommand request, CancellationToken ct)
    {
        await _repository.MarkAllAsReadAsync(request.RecipientId, ct);
    }
}
