using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;
using Dumble.NotificationService.Domain.Constants;
using Microsoft.Extensions.Logging;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class ReceiptIssuedConsumer(
    INotificationDeliveryService deliveryService,
    IDedupEventStore dedupEventStore,
    ILogger<ReceiptIssuedConsumer> logger
) : IConsumer<ReceiptIssuedEvent>
{
    public async Task Consume(ConsumeContext<ReceiptIssuedEvent> context)
    {
        var evt = context.Message;

        if (context.MessageId.HasValue)
        {
            if (!await dedupEventStore.TryClaimAsync(context.MessageId.Value.ToString(), nameof(ReceiptIssuedConsumer), context.CancellationToken))
            {
                logger.LogInformation("Dedup: {MessageId} already processed by {ConsumerType}", context.MessageId, nameof(ReceiptIssuedConsumer));
                return;
            }
        }

        var amount = evt.AmountCents / 100m;
        await deliveryService.DeliverAsync(
            new Notification
            {
                RecipientId = evt.UserId.ToString(),
                Type = NotificationTypes.Receipt,
                Title = "Receipt Available",
                Body = $"Your receipt for {amount:F2} {evt.Currency} is now available.",
                Data = new Dictionary<string, string>
                {
                    ["receiptId"] = evt.ReceiptId.ToString(),
                    ["amountCents"] = evt.AmountCents.ToString(),
                    ["currency"] = evt.Currency
                },
                CreatedAt = DateTime.UtcNow,
                ExpiresAt = DateTime.UtcNow.AddDays(365)
            },
            context.CancellationToken);
    }
}
