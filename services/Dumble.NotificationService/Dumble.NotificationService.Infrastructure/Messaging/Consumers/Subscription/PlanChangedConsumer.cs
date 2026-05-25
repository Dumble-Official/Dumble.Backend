using MassTransit;
using Microsoft.Extensions.Logging;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;
using Dumble.NotificationService.Domain.Constants;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class PlanChangedConsumer(
    INotificationDeliveryService deliveryService,
    IDedupEventStore dedupEventStore,
    ILogger<PlanChangedConsumer> logger
) : IConsumer<PlanChangedEvent>
{
    public async Task Consume(ConsumeContext<PlanChangedEvent> context)
    {
        var evt = context.Message;

        if (context.MessageId.HasValue)
        {
            if (!await dedupEventStore.TryClaimAsync(context.MessageId.Value.ToString(), nameof(PlanChangedConsumer), context.CancellationToken))
            {
                logger.LogInformation("Dedup: {MessageId} already processed by {ConsumerType}", context.MessageId, nameof(PlanChangedConsumer));
                return;
            }
        }

        if (evt.UserId is null)
        {
            logger.LogWarning("PlanChangedEvent has no UserId — payload may be POJO variant without user identifier");
            return;
        }
        var userId = evt.UserId.Value.ToString();
        var newPlan = evt.NewPlan ?? "unknown";
        await deliveryService.DeliverAsync(
            new Notification
            {
                RecipientId = userId,
                Type = NotificationTypes.PlanChange,
                Title = "Plan Changed",
                Body = $"Your plan has been changed to {newPlan}.",
                Data = new Dictionary<string, string>
                {
                    ["newPlan"] = newPlan,
                    ["userId"] = userId
                },
                CreatedAt = DateTime.UtcNow
            },
            context.CancellationToken);
    }
}
