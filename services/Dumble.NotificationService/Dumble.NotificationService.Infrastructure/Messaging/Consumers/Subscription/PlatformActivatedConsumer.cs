using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class PlatformActivatedConsumer(
    INotificationRepository notificationRepository,
    INotificationPreferenceRepository preferenceRepository,
    IDeviceTokenRepository deviceTokenRepository,
    IPushNotificationService pushService,
    INotificationHubService hubService
) : IConsumer<PlatformActivatedEvent>
{
    public async Task Consume(ConsumeContext<PlatformActivatedEvent> context)
    {
        var evt = context.Message;
        await NotificationDeliveryHelper.DeliverAsync(
            new Notification
            {
                RecipientId = evt.UserId.ToString(),
                Type = "PlanChange",
                Title = "Plan Activated",
                Body = $"Your {evt.PlanCode} plan is now active.",
                Data = new Dictionary<string, string>
                {
                    ["planCode"] = evt.PlanCode,
                    ["userId"] = evt.UserId.ToString()
                },
                CreatedAt = DateTime.UtcNow,
                ExpiresAt = DateTime.UtcNow.AddDays(30)
            },
            notificationRepository, preferenceRepository, deviceTokenRepository, pushService, hubService,
            context.CancellationToken);
    }
}
