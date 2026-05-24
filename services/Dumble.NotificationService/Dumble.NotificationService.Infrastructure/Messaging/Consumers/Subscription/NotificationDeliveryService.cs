using Microsoft.Extensions.Options;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Contracts.Notifications;
using Dumble.NotificationService.Domain.Models;
using Dumble.NotificationService.Infrastructure.Configuration;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

internal class NotificationDeliveryService(
    INotificationRepository notificationRepository,
    INotificationPreferenceRepository preferenceRepository,
    IDeviceTokenRepository deviceTokenRepository,
    IPushNotificationService pushService,
    INotificationHubService hubService,
    IOptions<NotificationSettings> settings
) : INotificationDeliveryService
{
    public async Task DeliverAsync(Notification notification, CancellationToken ct)
    {
        var pref = await preferenceRepository.GetByUserIdAsync(notification.RecipientId, ct);
        var channelPref = pref?.Preferences.GetValueOrDefault(notification.Type);

        if (channelPref?.InApp != false)
        {
            if (notification.ExpiresAt is null)
                notification.ExpiresAt = DateTime.UtcNow.AddDays(settings.Value.DefaultExpiryDays);

            await notificationRepository.CreateAsync(notification, ct);
            var response = new NotificationResponse(
                notification.Id, notification.Type, notification.Title, notification.Body,
                notification.Data, notification.IsRead, notification.CreatedAt);
            await hubService.SendNotificationAsync(notification.RecipientId, response, ct);

            var count = await notificationRepository.GetUnreadCountAsync(notification.RecipientId, ct);
            await hubService.SendUnreadCountAsync(notification.RecipientId, count, ct);
        }

        if (channelPref?.Push != false)
        {
            var devices = await deviceTokenRepository.GetByUserIdAsync(notification.RecipientId, ct);
            if (devices.Count > 0)
                await pushService.SendAsync(
                    devices.Select(d => d.Token).ToList(),
                    notification.Title, notification.Body, notification.Data, ct);
        }
    }
}
