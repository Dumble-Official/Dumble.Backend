using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Subscription;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;

public class SellerFrozenConsumer(
    INotificationRepository notificationRepository,
    INotificationPreferenceRepository preferenceRepository,
    IDeviceTokenRepository deviceTokenRepository,
    IPushNotificationService pushService,
    INotificationHubService hubService
) : IConsumer<SellerFrozenEvent>
{
    public async Task Consume(ConsumeContext<SellerFrozenEvent> context)
    {
        var evt = context.Message;
        await NotificationDeliveryHelper.DeliverAsync(
            new Notification
            {
                RecipientId = evt.SellerId.ToString(),
                Type = "SellerAccount",
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
                CreatedAt = DateTime.UtcNow,
                ExpiresAt = DateTime.UtcNow.AddDays(30)
            },
            notificationRepository, preferenceRepository, deviceTokenRepository, pushService, hubService,
            context.CancellationToken);
    }
}
