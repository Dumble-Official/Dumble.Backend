using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;
using Dumble.NotificationService.Domain.Constants;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class BundleActivatedConsumer(
    INotificationRepository notificationRepository,
    INotificationPreferenceRepository preferenceRepository,
    IDeviceTokenRepository deviceTokenRepository,
    IPushNotificationService pushService,
    INotificationHubService hubService
) : IConsumer<BundleActivatedEvent>
{
    public async Task Consume(ConsumeContext<BundleActivatedEvent> context)
    {
        var evt = context.Message;
        await NotificationDeliveryHelper.DeliverAsync(
            new Notification
            {
                RecipientId = evt.ParticipantId.ToString(),
                Type = NotificationTypes.BundleSubscription,
                Title = "Subscription Active",
                Body = $"Your subscription to \"{evt.BundleName}\" is now active for {evt.DurationDays} days.",
                Data = new Dictionary<string, string>
                {
                    ["subscriptionId"] = evt.SubscriptionId.ToString(),
                    ["bundleName"] = evt.BundleName,
                    ["sellerId"] = evt.SellerId.ToString(),
                    ["durationDays"] = evt.DurationDays.ToString()
                },
                CreatedAt = DateTime.UtcNow,
                ExpiresAt = DateTime.UtcNow.AddDays(30)
            },
            notificationRepository, preferenceRepository, deviceTokenRepository, pushService, hubService,
            context.CancellationToken);
    }
}
