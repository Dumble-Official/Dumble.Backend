using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;
using Dumble.NotificationService.Domain.Constants;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class SellerBannedConsumer(
    INotificationRepository notificationRepository,
    INotificationPreferenceRepository preferenceRepository,
    IDeviceTokenRepository deviceTokenRepository,
    IPushNotificationService pushService,
    INotificationHubService hubService
) : IConsumer<SellerBannedEvent>
{
    public async Task Consume(ConsumeContext<SellerBannedEvent> context)
    {
        var evt = context.Message;
        await NotificationDeliveryHelper.DeliverAsync(
            new Notification
            {
                RecipientId = evt.SellerId.ToString(),
                Type = NotificationTypes.SellerAccount,
                Title = "Account Banned",
                Body = evt.Reason is not null
                    ? $"Your seller account has been banned: {evt.Reason}"
                    : "Your seller account has been banned",
                Data = new Dictionary<string, string>
                {
                    ["sellerId"] = evt.SellerId.ToString(),
                    ["reason"] = evt.Reason ?? ""
                },
                CreatedAt = DateTime.UtcNow,
                ExpiresAt = DateTime.UtcNow.AddDays(90)
            },
            notificationRepository, preferenceRepository, deviceTokenRepository, pushService, hubService,
            context.CancellationToken);
    }
}
