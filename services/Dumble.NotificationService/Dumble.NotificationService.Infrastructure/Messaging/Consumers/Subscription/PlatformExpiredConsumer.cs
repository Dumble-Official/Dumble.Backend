using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;
using Dumble.NotificationService.Domain.Constants;
using Microsoft.Extensions.Logging;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class PlatformExpiredConsumer(
    INotificationDeliveryService deliveryService,
    IDedupEventStore dedupEventStore,
    ILogger<PlatformExpiredConsumer> logger
) : IConsumer<PlatformExpiredEvent>
{
    public async Task Consume(ConsumeContext<PlatformExpiredEvent> context)
    {
        var evt = context.Message;

        if (context.MessageId.HasValue)
        {
            if (!await dedupEventStore.TryClaimAsync(context.MessageId.Value.ToString(), nameof(PlatformExpiredConsumer), context.CancellationToken))
            {
                logger.LogInformation("Dedup: {MessageId} already processed by {ConsumerType}", context.MessageId, nameof(PlatformExpiredConsumer));
                return;
            }
        }

        await deliveryService.DeliverAsync(
            new Notification
            {
                RecipientId = evt.UserId.ToString(),
                Type = NotificationTypes.PlanChange,
                Title = "Plan Expired",
                Body = "Your platform plan has expired.",
                Data = new Dictionary<string, string>
                {
                    ["userId"] = evt.UserId.ToString(),
                    ["reason"] = evt.Reason ?? ""
                },
                CreatedAt = DateTime.UtcNow,
                ExpiresAt = DateTime.UtcNow.AddDays(30)
            },
            context.CancellationToken);
    }
}
