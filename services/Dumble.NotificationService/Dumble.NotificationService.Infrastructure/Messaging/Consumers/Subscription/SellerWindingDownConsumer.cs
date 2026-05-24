using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;
using Dumble.NotificationService.Domain.Constants;
using Microsoft.Extensions.Logging;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class SellerWindingDownConsumer(
    INotificationDeliveryService deliveryService,
    IDedupEventStore dedupEventStore,
    ILogger<SellerWindingDownConsumer> logger
) : IConsumer<SellerWindingDownEvent>
{
    public async Task Consume(ConsumeContext<SellerWindingDownEvent> context)
    {
        var evt = context.Message;

        if (context.MessageId.HasValue)
        {
            if (!await dedupEventStore.TryClaimAsync(context.MessageId.Value.ToString(), nameof(SellerWindingDownConsumer), context.CancellationToken))
            {
                logger.LogInformation("Dedup: {MessageId} already processed by {ConsumerType}", context.MessageId, nameof(SellerWindingDownConsumer));
                return;
            }
        }

        await deliveryService.DeliverAsync(
            new Notification
            {
                RecipientId = evt.SellerId.ToString(),
                Type = NotificationTypes.SellerAccount,
                Title = "Account Winding Down",
                Body = evt.Reason is not null
                    ? $"Your seller account is winding down: {evt.Reason}"
                    : "Your seller account is winding down",
                Data = new Dictionary<string, string>
                {
                    ["sellerId"] = evt.SellerId.ToString(),
                    ["reason"] = evt.Reason ?? ""
                },
                CreatedAt = DateTime.UtcNow,
                ExpiresAt = DateTime.UtcNow.AddDays(60)
            },
            context.CancellationToken);
    }
}
