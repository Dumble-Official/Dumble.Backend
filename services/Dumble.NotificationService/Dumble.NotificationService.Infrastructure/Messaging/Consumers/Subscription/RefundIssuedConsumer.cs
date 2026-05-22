using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class RefundIssuedConsumer(
    INotificationRepository notificationRepository,
    INotificationPreferenceRepository preferenceRepository,
    IDeviceTokenRepository deviceTokenRepository,
    IPushNotificationService pushService,
    INotificationHubService hubService
) : IConsumer<RefundIssuedEvent>
{
    public async Task Consume(ConsumeContext<RefundIssuedEvent> context)
    {
        var evt = context.Message;
        var amount = evt.AmountCents / 100m;
        await NotificationDeliveryHelper.DeliverAsync(
            new Notification
            {
                RecipientId = evt.SubscriptionId.ToString(),
                Type = "Refund",
                Title = "Refund Processed",
                Body = $"A refund of {amount:F2} has been processed{(!string.IsNullOrEmpty(evt.Reason) ? $": {evt.Reason}" : ".")}",
                Data = new Dictionary<string, string>
                {
                    ["refundId"] = evt.RefundId.ToString(),
                    ["subscriptionId"] = evt.SubscriptionId.ToString(),
                    ["amountCents"] = evt.AmountCents.ToString()
                },
                CreatedAt = DateTime.UtcNow,
                ExpiresAt = DateTime.UtcNow.AddDays(90)
            },
            notificationRepository, preferenceRepository, deviceTokenRepository, pushService, hubService,
            context.CancellationToken);
    }
}
