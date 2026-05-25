using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;
using Dumble.NotificationService.Domain.Constants;
using Microsoft.Extensions.Logging;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class RefundIssuedConsumer(
    INotificationDeliveryService deliveryService,
    IDedupEventStore dedupEventStore,
    ILogger<RefundIssuedConsumer> logger
) : IConsumer<RefundIssuedEvent>
{
    public async Task Consume(ConsumeContext<RefundIssuedEvent> context)
    {
        var evt = context.Message;

        if (context.MessageId.HasValue)
        {
            if (!await dedupEventStore.TryClaimAsync(context.MessageId.Value.ToString(), nameof(RefundIssuedConsumer), context.CancellationToken))
            {
                logger.LogInformation("Dedup: {MessageId} already processed by {ConsumerType}", context.MessageId, nameof(RefundIssuedConsumer));
                return;
            }
        }

        var amount = evt.AmountCents / 100m;
        await deliveryService.DeliverAsync(
            new Notification
            {
                RecipientId = evt.ParticipantId.ToString(),
                Type = NotificationTypes.Refund,
                Title = "Refund Processed",
                Body = $"A refund of {amount:F2} has been processed.",
                Data = new Dictionary<string, string>
                {
                    ["subscriptionId"] = evt.SubscriptionId.ToString(),
                    ["participantId"] = evt.ParticipantId.ToString(),
                    ["amountCents"] = evt.AmountCents.ToString()
                },
                CreatedAt = DateTime.UtcNow
            },
            context.CancellationToken);
    }
}
