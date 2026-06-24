using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.NotificationService.Domain.Constants;
using Dumble.SharedKernel.Events.Schedule;
using Microsoft.Extensions.Logging;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Schedule;

/// <summary>
/// Consumes the Java Schedule service's <c>schedule.reminder.due</c> event and
/// turns it into an end-of-day "you still have items to do" notification.
/// </summary>
public class ScheduleReminderConsumer(
    INotificationDeliveryService deliveryService,
    IDedupEventStore dedupEventStore,
    ILogger<ScheduleReminderConsumer> logger
) : IConsumer<ScheduleReminderDueEvent>
{
    public async Task Consume(ConsumeContext<ScheduleReminderDueEvent> context)
    {
        var evt = context.Message;

        if (context.MessageId.HasValue)
        {
            if (!await dedupEventStore.TryClaimAsync(context.MessageId.Value.ToString(), nameof(ScheduleReminderConsumer), context.CancellationToken))
            {
                logger.LogInformation("Dedup: {MessageId} already processed by {ConsumerType}", context.MessageId, nameof(ScheduleReminderConsumer));
                return;
            }
        }

        var itemWord = evt.PendingCount == 1 ? "item" : "items";

        await deliveryService.DeliverAsync(
            new Notification
            {
                RecipientId = evt.UserId.ToString(),
                Type = NotificationTypes.ScheduleReminder,
                Title = "Don't forget today's plan",
                Body = $"You still have {evt.PendingCount} {itemWord} left to do today.",
                Data = new Dictionary<string, string>
                {
                    ["date"] = evt.Date,
                    ["pendingCount"] = evt.PendingCount.ToString(),
                    ["timezone"] = evt.Timezone
                },
                CreatedAt = DateTime.UtcNow
            },
            context.CancellationToken);
    }
}
