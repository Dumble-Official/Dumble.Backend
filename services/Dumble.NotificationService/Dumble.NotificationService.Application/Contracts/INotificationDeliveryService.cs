using Dumble.NotificationService.Domain.Models;

namespace Dumble.NotificationService.Application.Contracts;

public interface INotificationDeliveryService
{
    Task DeliverAsync(Notification notification, CancellationToken ct);
}
