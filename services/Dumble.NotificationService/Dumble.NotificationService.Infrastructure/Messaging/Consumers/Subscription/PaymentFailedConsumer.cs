using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class PaymentFailedConsumer(
    INotificationRepository notificationRepository,
    INotificationPreferenceRepository preferenceRepository,
    IDeviceTokenRepository deviceTokenRepository,
    IPushNotificationService pushService,
    INotificationHubService hubService
) : IConsumer<PaymentFailedEvent>
{
    public async Task Consume(ConsumeContext<PaymentFailedEvent> context)
    {
        var evt = context.Message;
        var recipientId = (evt.UserId ?? evt.SubscriptionId)?.ToString();
        if (recipientId is null) return;

        await NotificationDeliveryHelper.DeliverAsync(
            new Notification
            {
                RecipientId = recipientId,
                Type = "PaymentIssue",
                Title = "Payment Failed",
                Body = $"Payment attempt {evt.Attempt} failed. We'll retry automatically.",
                Data = new Dictionary<string, string>
                {
                    ["subscriptionId"] = evt.SubscriptionId?.ToString() ?? "",
                    ["userId"] = evt.UserId?.ToString() ?? "",
                    ["attempt"] = evt.Attempt.ToString()
                },
                CreatedAt = DateTime.UtcNow,
                ExpiresAt = DateTime.UtcNow.AddDays(30)
            },
            notificationRepository, preferenceRepository, deviceTokenRepository, pushService, hubService,
            context.CancellationToken);
    }
}
