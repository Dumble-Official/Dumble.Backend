using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Constants;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;
using Microsoft.Extensions.Logging;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class RenewalPromptConsumer(
    INotificationDeliveryService deliveryService,
    IDedupEventStore dedupEventStore,
    ILogger<RenewalPromptConsumer> logger
) : IConsumer<RenewalPromptEvent>
{
    public async Task Consume(ConsumeContext<RenewalPromptEvent> context)
    {
        var evt = context.Message;

        if (context.MessageId.HasValue)
        {
            if (!await dedupEventStore.TryClaimAsync(context.MessageId.Value.ToString(), nameof(RenewalPromptConsumer), context.CancellationToken))
            {
                logger.LogInformation("Dedup: {MessageId} already processed by {ConsumerType}", context.MessageId, nameof(RenewalPromptConsumer));
                return;
            }
        }

        var recipientId = (evt.UserId ?? evt.ParticipantId)?.ToString();
        if (recipientId is null)
        {
            logger.LogWarning("RenewalPromptEvent {EventId} has no resolvable recipient", evt.EventId);
            return;
        }

        var amount = evt.AmountCents.HasValue ? $"{evt.AmountCents.Value / 100m:F2} {evt.Currency}" : "";
        await deliveryService.DeliverAsync(
            new Notification
            {
                RecipientId = recipientId,
                Type = NotificationTypes.Renewal,
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
            context.CancellationToken);
    }
}
