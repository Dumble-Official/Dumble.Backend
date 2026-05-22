using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class PaymentFailedFinalConsumer(
    INotificationRepository notificationRepository,
    INotificationPreferenceRepository preferenceRepository,
    IDeviceTokenRepository deviceTokenRepository,
    IPushNotificationService pushService,
    INotificationHubService hubService
) : IConsumer<PaymentFailedFinalEvent>
{
    public async Task Consume(ConsumeContext<PaymentFailedFinalEvent> context)
    {
        var evt = context.Message;
        var recipientId = (evt.UserId ?? evt.SubscriptionId)?.ToString();
        if (recipientId is null) return;

        await NotificationDeliveryHelper.DeliverAsync(
            new Notification
            {
                RecipientId = recipientId,
                Type = "PaymentIssue",
                Title = "Payment Failed — Subscription Expired",
                Body = "All payment retry attempts were exhausted. Your subscription has expired.",
                Data = new Dictionary<string, string>
                {
                    ["subscriptionId"] = evt.SubscriptionId?.ToString() ?? "",
                    ["userId"] = evt.UserId?.ToString() ?? ""
                },
                CreatedAt = DateTime.UtcNow,
                ExpiresAt = DateTime.UtcNow.AddDays(60)
            },
            notificationRepository, preferenceRepository, deviceTokenRepository, pushService, hubService,
            context.CancellationToken);
    }
}
