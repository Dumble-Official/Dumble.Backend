using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;
using Dumble.NotificationService.Domain.Constants;
using Microsoft.Extensions.Logging;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class SellerUnfrozenConsumer(
    INotificationDeliveryService deliveryService,
    IDedupEventStore dedupEventStore,
    ILogger<SellerUnfrozenConsumer> logger
) : IConsumer<SellerUnfrozenEvent>
{
    public async Task Consume(ConsumeContext<SellerUnfrozenEvent> context)
    {
        var evt = context.Message;

        if (context.MessageId.HasValue)
        {
            if (!await dedupEventStore.TryClaimAsync(context.MessageId.Value.ToString(), nameof(SellerUnfrozenConsumer), context.CancellationToken))
            {
                logger.LogInformation("Dedup: {MessageId} already processed by {ConsumerType}", context.MessageId, nameof(SellerUnfrozenConsumer));
                return;
            }
        }

        await deliveryService.DeliverAsync(
            new Notification
            {
                RecipientId = evt.SellerId.ToString(),
                Type = NotificationTypes.SellerAccount,
                Title = "Account Unfrozen",
                Body = "Your seller account has been unfrozen. You can now accept new subscriptions.",
                Data = new Dictionary<string, string>
                {
                    ["sellerId"] = evt.SellerId.ToString()
                },
                CreatedAt = DateTime.UtcNow,
                ExpiresAt = DateTime.UtcNow.AddDays(30)
            },
            context.CancellationToken);
    }
}
