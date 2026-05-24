using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;
using Dumble.NotificationService.Domain.Constants;
using Microsoft.Extensions.Logging;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class SellerBannedConsumer(
    INotificationDeliveryService deliveryService,
    IDedupEventStore dedupEventStore,
    ILogger<SellerBannedConsumer> logger
) : IConsumer<SellerBannedEvent>
{
    public async Task Consume(ConsumeContext<SellerBannedEvent> context)
    {
        var evt = context.Message;

        if (context.MessageId.HasValue)
        {
            if (!await dedupEventStore.TryClaimAsync(context.MessageId.Value.ToString(), nameof(SellerBannedConsumer), context.CancellationToken))
            {
                logger.LogInformation("Dedup: {MessageId} already processed by {ConsumerType}", context.MessageId, nameof(SellerBannedConsumer));
                return;
            }
        }

        await deliveryService.DeliverAsync(
            new Notification
            {
                RecipientId = evt.SellerId.ToString(),
                Type = NotificationTypes.SellerAccount,
                Title = "Account Banned",
                Body = evt.Reason is not null
                    ? $"Your seller account has been banned: {evt.Reason}"
                    : "Your seller account has been banned",
                Data = new Dictionary<string, string>
                {
                    ["sellerId"] = evt.SellerId.ToString(),
                    ["reason"] = evt.Reason ?? ""
                },
                CreatedAt = DateTime.UtcNow,
                ExpiresAt = DateTime.UtcNow.AddDays(90)
            },
            context.CancellationToken);
    }
}
