using Dumble.NotificationService.Contracts.Notifications;

namespace Dumble.NotificationService.Application.Contracts;

public interface INotificationHubService
{
    Task SendNotificationAsync(string recipientId, NotificationResponse notification, CancellationToken ct = default);
    Task SendUnreadCountAsync(string recipientId, int count, CancellationToken ct = default);
}
