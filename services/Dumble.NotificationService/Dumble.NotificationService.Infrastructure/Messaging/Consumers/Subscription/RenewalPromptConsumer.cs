using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class RenewalPromptConsumer(
    INotificationRepository notificationRepository,
    INotificationPreferenceRepository preferenceRepository,
    IDeviceTokenRepository deviceTokenRepository,
    IPushNotificationService pushService,
    INotificationHubService hubService
) : IConsumer<RenewalPromptEvent>
{
    public async Task Consume(ConsumeContext<RenewalPromptEvent> context)
    {
        var evt = context.Message;
        var recipientId = (evt.UserId ?? evt.ParticipantId)?.ToString();
        if (recipientId is null) return;

        var amount = evt.AmountCents.HasValue ? $"{evt.AmountCents.Value / 100m:F2} {evt.Currency}" : "";
        await NotificationDeliveryHelper.DeliverAsync(
            new Notification
            {
                RecipientId = recipientId,
                Type = "Renewal",
                Title = "Renewal Needed",
                Body = $"Your subscription renewal requires authorization{(amount.Length > 0 ? $" ({amount})" : "")}.",
                Data = new Dictionary<string, string>
                {
                    ["subscriptionId"] = evt.SubscriptionId?.ToString() ?? "",
                    ["amountCents"] = evt.AmountCents?.ToString() ?? "",
                    ["currency"] = evt.Currency ?? ""
                },
                CreatedAt = DateTime.UtcNow,
                ExpiresAt = DateTime.UtcNow.AddDays(14)
            },
            notificationRepository, preferenceRepository, deviceTokenRepository, pushService, hubService,
            context.CancellationToken);
    }
}
