using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;
using Dumble.NotificationService.Domain.Constants;
using Microsoft.Extensions.Logging;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class BundleActivatedConsumer(
    INotificationDeliveryService deliveryService,
    IDedupEventStore dedupEventStore,
    ILogger<BundleActivatedConsumer> logger
) : IConsumer<BundleActivatedEvent>
{
    public async Task Consume(ConsumeContext<BundleActivatedEvent> context)
    {
        var evt = context.Message;

        if (context.MessageId.HasValue)
        {
            if (!await dedupEventStore.TryClaimAsync(context.MessageId.Value.ToString(), nameof(BundleActivatedConsumer), context.CancellationToken))
            {
                logger.LogInformation("Dedup: {MessageId} already processed by {ConsumerType}", context.MessageId, nameof(BundleActivatedConsumer));
                return;
            }
        }

        await deliveryService.DeliverAsync(
            new Notification
            {
                RecipientId = evt.ParticipantId.ToString(),
                Type = NotificationTypes.BundleSubscription,
                Title = "Subscription Active",
                Body = $"Your subscription to \"{evt.BundleName}\" is now active for {evt.DurationDays} days.",
                Data = new Dictionary<string, string>
                {
                    ["subscriptionId"] = evt.Id.ToString(),
                    ["bundleName"] = evt.BundleName,
                    ["sellerId"] = evt.SellerId.ToString(),
                    ["durationDays"] = evt.DurationDays.ToString()
                },
                CreatedAt = DateTime.UtcNow,
                ExpiresAt = DateTime.UtcNow.AddDays(30)
            },
            context.CancellationToken);
    }
}
