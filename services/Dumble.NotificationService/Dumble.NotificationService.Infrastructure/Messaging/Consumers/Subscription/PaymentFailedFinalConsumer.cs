using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Constants;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;
using Microsoft.Extensions.Logging;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class PaymentFailedFinalConsumer(
    INotificationDeliveryService deliveryService,
    IDedupEventStore dedupEventStore,
    ILogger<PaymentFailedFinalConsumer> logger
) : IConsumer<PaymentFailedFinalEvent>
{
    public async Task Consume(ConsumeContext<PaymentFailedFinalEvent> context)
    {
        var evt = context.Message;

        if (context.MessageId.HasValue)
        {
            if (!await dedupEventStore.TryClaimAsync(context.MessageId.Value.ToString(), nameof(PaymentFailedFinalConsumer), context.CancellationToken))
            {
                logger.LogInformation("Dedup: {MessageId} already processed by {ConsumerType}", context.MessageId, nameof(PaymentFailedFinalConsumer));
                return;
            }
        }

        var recipientId = (evt.UserId ?? evt.SubscriptionId)?.ToString();
        if (recipientId is null)
        {
            logger.LogWarning("PaymentFailedFinalEvent {EventId} has no resolvable recipient (UserId and SubscriptionId both null)", evt.EventId);
            return;
        }

        await deliveryService.DeliverAsync(
            new Notification
            {
                RecipientId = recipientId,
                Type = NotificationTypes.PaymentIssue,
                Title = "Payment Failed — Subscription Expired",
                Body = "All payment retry attempts were exhausted. Your subscription has expired.",
                Data = new Dictionary<string, string>
                {
                    ["subscriptionId"] = evt.SubscriptionId?.ToString() ?? "",
                    ["userId"] = evt.UserId?.ToString() ?? ""
                },
                CreatedAt = DateTime.UtcNow
            },
            context.CancellationToken);
    }
}
