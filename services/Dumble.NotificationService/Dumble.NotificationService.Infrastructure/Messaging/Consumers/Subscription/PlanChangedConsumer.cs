using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class PlanChangedConsumer(
    INotificationRepository notificationRepository,
    INotificationPreferenceRepository preferenceRepository,
    IDeviceTokenRepository deviceTokenRepository,
    IPushNotificationService pushService,
    INotificationHubService hubService
) : IConsumer<PlanChangedEvent>
{
    public async Task Consume(ConsumeContext<PlanChangedEvent> context)
    {
        var evt = context.Message;
        await NotificationDeliveryHelper.DeliverAsync(
            new Notification
            {
                RecipientId = evt.UserId.ToString(),
                Type = "PlanChange",
                Title = "Plan Changed",
                Body = $"Your plan has been changed to {evt.NewPlan}.",
                Data = new Dictionary<string, string>
                {
                    ["newPlan"] = evt.NewPlan,
                    ["userId"] = evt.UserId.ToString()
                },
                CreatedAt = DateTime.UtcNow,
                ExpiresAt = DateTime.UtcNow.AddDays(30)
            },
            notificationRepository, preferenceRepository, deviceTokenRepository, pushService, hubService,
            context.CancellationToken);
    }
}
