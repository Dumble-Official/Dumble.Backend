using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;
using Dumble.NotificationService.Domain.Constants;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class PlatformExpiredConsumer(
    INotificationRepository notificationRepository,
    INotificationPreferenceRepository preferenceRepository,
    IDeviceTokenRepository deviceTokenRepository,
    IPushNotificationService pushService,
    INotificationHubService hubService
) : IConsumer<PlatformExpiredEvent>
{
    public async Task Consume(ConsumeContext<PlatformExpiredEvent> context)
    {
        var evt = context.Message;
        await NotificationDeliveryHelper.DeliverAsync(
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
            notificationRepository, preferenceRepository, deviceTokenRepository, pushService, hubService,
            context.CancellationToken);
    }
}
