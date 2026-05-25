using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;
using Dumble.NotificationService.Domain.Constants;
using Microsoft.Extensions.Logging;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class SellerFrozenConsumer(
    INotificationDeliveryService deliveryService,
    IDedupEventStore dedupEventStore,
    ILogger<SellerFrozenConsumer> logger
) : IConsumer<SellerFrozenEvent>
{
    public async Task Consume(ConsumeContext<SellerFrozenEvent> context)
    {
        var evt = context.Message;

        if (context.MessageId.HasValue)
        {
            if (!await dedupEventStore.TryClaimAsync(context.MessageId.Value.ToString(), nameof(SellerFrozenConsumer), context.CancellationToken))
            {
                logger.LogInformation("Dedup: {MessageId} already processed by {ConsumerType}", context.MessageId, nameof(SellerFrozenConsumer));
                return;
            }
        }

        await deliveryService.DeliverAsync(
            new Notification
            {
                RecipientId = evt.SellerId.ToString(),
                Type = NotificationTypes.SellerAccount,
                Title = "Account Frozen",
                Body = evt.Reason is not null
                    ? $"Your seller account has been frozen: {evt.Reason}"
                    : "Your seller account has been frozen",
                Data = new Dictionary<string, string>
                {
                    ["sellerId"] = evt.SellerId.ToString(),
                    ["reason"] = evt.Reason ?? "",
                    ["frozenUntil"] = evt.FrozenUntil?.ToString("O") ?? ""
                },
                CreatedAt = DateTime.UtcNow
            },
            context.CancellationToken);
    }
}
