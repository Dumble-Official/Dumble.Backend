using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Constants;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;
using Microsoft.Extensions.Logging;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class PaymentFailedConsumer(
    INotificationRepository notificationRepository,
    INotificationPreferenceRepository preferenceRepository,
    IDeviceTokenRepository deviceTokenRepository,
    IPushNotificationService pushService,
    INotificationHubService hubService,
    ILogger<PaymentFailedConsumer> logger
) : IConsumer<PaymentFailedEvent>
{
    public async Task Consume(ConsumeContext<PaymentFailedEvent> context)
    {
        var evt = context.Message;
        var recipientId = (evt.UserId ?? evt.SubscriptionId)?.ToString();
        if (recipientId is null)
        {
            logger.LogWarning("PaymentFailedEvent {EventId} has no resolvable recipient (UserId and SubscriptionId both null)", evt.EventId);
            return;
        }

        await NotificationDeliveryHelper.DeliverAsync(
            new Notification
            {
                RecipientId = recipientId,
                Type = NotificationTypes.PaymentIssue,
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
