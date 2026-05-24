using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;
using Dumble.NotificationService.Domain.Constants;
using Microsoft.Extensions.Logging;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class BundleExpiredConsumer(
    INotificationDeliveryService deliveryService,
    IDedupEventStore dedupEventStore,
    ILogger<BundleExpiredConsumer> logger
) : IConsumer<BundleExpiredEvent>
{
    public async Task Consume(ConsumeContext<BundleExpiredEvent> context)
    {
        var evt = context.Message;

        if (context.MessageId.HasValue)
        {
            if (!await dedupEventStore.TryClaimAsync(context.MessageId.Value.ToString(), nameof(BundleExpiredConsumer), context.CancellationToken))
            {
                logger.LogInformation("Dedup: {MessageId} already processed by {ConsumerType}", context.MessageId, nameof(BundleExpiredConsumer));
                return;
            }
        }

        await deliveryService.DeliverAsync(
            new Notification
            {
                RecipientId = evt.SubscriptionId.ToString(),
                Type = NotificationTypes.BundleSubscription,
                Title = "Subscription Expired",
                Body = "Your bundle subscription has expired.",
                Data = new Dictionary<string, string>
                {
                    ["subscriptionId"] = evt.SubscriptionId.ToString(),
                    ["reason"] = evt.Reason ?? ""
                },
                CreatedAt = DateTime.UtcNow,
                ExpiresAt = DateTime.UtcNow.AddDays(60)
            },
            context.CancellationToken);
    }
}
