using MassTransit;
using Microsoft.Extensions.Logging;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;
using Dumble.NotificationService.Domain.Constants;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class PlatformActivatedConsumer(
    INotificationDeliveryService deliveryService,
    IDedupEventStore dedupEventStore,
    ILogger<PlatformActivatedConsumer> logger
) : IConsumer<PlatformActivatedEvent>
{
    public async Task Consume(ConsumeContext<PlatformActivatedEvent> context)
    {
        var evt = context.Message;

        if (context.MessageId.HasValue)
        {
            if (!await dedupEventStore.TryClaimAsync(context.MessageId.Value.ToString(), nameof(PlatformActivatedConsumer), context.CancellationToken))
            {
                logger.LogInformation("Dedup: {MessageId} already processed by {ConsumerType}", context.MessageId, nameof(PlatformActivatedConsumer));
                return;
            }
        }

        if (evt.UserId is null)
        {
            logger.LogWarning("PlatformActivatedEvent has no UserId — payload lacks user identifier; Java POJO may need to be updated");
            return;
        }
        await deliveryService.DeliverAsync(
            new Notification
            {
                RecipientId = evt.UserId.Value.ToString(),
                Type = NotificationTypes.PlanChange,
                Title = "Plan Activated",
                Body = $"Your {evt.PlanCode} plan is now active.",
                Data = new Dictionary<string, string>
                {
                    ["planCode"] = evt.PlanCode,
                    ["userId"] = evt.UserId.Value.ToString()
                },
                CreatedAt = DateTime.UtcNow
            },
            context.CancellationToken);
    }
}
