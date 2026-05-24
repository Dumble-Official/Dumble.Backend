using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;
using Dumble.NotificationService.Domain.Constants;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class ReceiptIssuedConsumer(
    INotificationRepository notificationRepository,
    INotificationPreferenceRepository preferenceRepository,
    IDeviceTokenRepository deviceTokenRepository,
    IPushNotificationService pushService,
    INotificationHubService hubService
) : IConsumer<ReceiptIssuedEvent>
{
    public async Task Consume(ConsumeContext<ReceiptIssuedEvent> context)
    {
        var evt = context.Message;
        var amount = evt.AmountCents / 100m;
        await NotificationDeliveryHelper.DeliverAsync(
            new Notification
            {
                RecipientId = evt.UserId.ToString(),
                Type = NotificationTypes.Receipt,
                Title = "Receipt Available",
                Body = $"Your receipt for {amount:F2} {evt.Currency} is now available.",
                Data = new Dictionary<string, string>
                {
                    ["receiptId"] = evt.ReceiptId.ToString(),
                    ["amountCents"] = evt.AmountCents.ToString(),
                    ["currency"] = evt.Currency
                },
                CreatedAt = DateTime.UtcNow,
                ExpiresAt = DateTime.UtcNow.AddDays(365)
            },
            notificationRepository, preferenceRepository, deviceTokenRepository, pushService, hubService,
            context.CancellationToken);
    }
}
