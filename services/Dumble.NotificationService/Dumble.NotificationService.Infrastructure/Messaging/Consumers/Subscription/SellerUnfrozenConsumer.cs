using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;
using Dumble.NotificationService.Domain.Constants;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class SellerUnfrozenConsumer(
    INotificationRepository notificationRepository,
    INotificationPreferenceRepository preferenceRepository,
    IDeviceTokenRepository deviceTokenRepository,
    IPushNotificationService pushService,
    INotificationHubService hubService
) : IConsumer<SellerUnfrozenEvent>
{
    public async Task Consume(ConsumeContext<SellerUnfrozenEvent> context)
    {
        var evt = context.Message;
        await NotificationDeliveryHelper.DeliverAsync(
            new Notification
            {
                RecipientId = evt.SellerId.ToString(),
                Type = NotificationTypes.SellerAccount,
                Title = "Account Unfrozen",
                Body = "Your seller account has been unfrozen. You can now accept new subscriptions.",
                Data = new Dictionary<string, string>
                {
                    ["sellerId"] = evt.SellerId.ToString()
                },
                CreatedAt = DateTime.UtcNow,
                ExpiresAt = DateTime.UtcNow.AddDays(30)
            },
            notificationRepository, preferenceRepository, deviceTokenRepository, pushService, hubService,
            context.CancellationToken);
    }
}
