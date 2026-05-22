using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class ChargebackProcessedConsumer(
    INotificationRepository notificationRepository,
    INotificationPreferenceRepository preferenceRepository,
    IDeviceTokenRepository deviceTokenRepository,
    IPushNotificationService pushService,
    INotificationHubService hubService
) : IConsumer<ChargebackProcessedEvent>
{
    public async Task Consume(ConsumeContext<ChargebackProcessedEvent> context)
    {
        var evt = context.Message;
        await NotificationDeliveryHelper.DeliverAsync(
            new Notification
            {
                RecipientId = evt.SubscriptionId.ToString(),
                Type = "Chargeback",
                Title = "Chargeback Filed",
                Body = $"A chargeback of {(evt.AmountCents / 100m):F2} has been processed for your subscription.",
                Data = new Dictionary<string, string>
                {
                    ["chargebackId"] = evt.ChargebackId.ToString(),
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
