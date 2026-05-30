using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;
using Dumble.NotificationService.Domain.Constants;
using Microsoft.Extensions.Logging;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class ChargebackProcessedConsumer(
    INotificationDeliveryService deliveryService,
    IDedupEventStore dedupEventStore,
    ILogger<ChargebackProcessedConsumer> logger
) : IConsumer<ChargebackProcessedEvent>
{
    public async Task Consume(ConsumeContext<ChargebackProcessedEvent> context)
    {
        var evt = context.Message;

        if (context.MessageId.HasValue)
        {
            if (!await dedupEventStore.TryClaimAsync(context.MessageId.Value.ToString(), nameof(ChargebackProcessedConsumer), context.CancellationToken))
            {
                logger.LogInformation("Dedup: {MessageId} already processed by {ConsumerType}", context.MessageId, nameof(ChargebackProcessedConsumer));
                return;
            }
        }

        await deliveryService.DeliverAsync(
            new Notification
            {
                RecipientId = evt.ParticipantId.ToString(),
                Type = NotificationTypes.Chargeback,
                Title = "Chargeback Filed",
                Body = $"A chargeback of {(evt.ChargebackCents / 100m):F2} has been processed for your subscription.",
                Data = new Dictionary<string, string>
                {
                    ["subscriptionId"] = evt.SubscriptionId.ToString(),
                    ["chargebackCents"] = evt.ChargebackCents.ToString(),
                    ["lockedCents"] = evt.LockedCents.ToString(),
                    ["partial"] = evt.Partial.ToString()
                },
                CreatedAt = DateTime.UtcNow
            },
            context.CancellationToken);
    }
}
