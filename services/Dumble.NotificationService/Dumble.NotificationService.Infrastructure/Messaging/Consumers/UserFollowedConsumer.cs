using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Contracts.Notifications;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Social;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers;

public class UserFollowedConsumer : IConsumer<UserFollowedEvent>
{
    private readonly INotificationRepository _notificationRepository;
    private readonly INotificationPreferenceRepository _preferenceRepository;
    private readonly IDeviceTokenRepository _deviceTokenRepository;
    private readonly IPushNotificationService _pushService;
    private readonly INotificationHubService _hubService;

    public UserFollowedConsumer(
        INotificationRepository notificationRepository,
        INotificationPreferenceRepository preferenceRepository,
        IDeviceTokenRepository deviceTokenRepository,
        IPushNotificationService pushService,
        INotificationHubService hubService)
    {
        _notificationRepository = notificationRepository;
        _preferenceRepository = preferenceRepository;
        _deviceTokenRepository = deviceTokenRepository;
        _pushService = pushService;
        _hubService = hubService;
    }

    public async Task Consume(ConsumeContext<UserFollowedEvent> context)
    {
        var evt = context.Message;

        var notification = new Notification
        {
            RecipientId = evt.FolloweeId,
            Type = "NewFollower",
            Title = "New Follower",
            Body = $"{evt.FollowerName} started following you",
            Data = new Dictionary<string, string>
            {
                ["followerId"] = evt.FollowerId,
                ["followerName"] = evt.FollowerName,
                ["followerImage"] = evt.FollowerImage ?? ""
            },
            CreatedAt = DateTime.UtcNow,
            ExpiresAt = DateTime.UtcNow.AddDays(30)
        };

        var pref = await _preferenceRepository.GetByUserIdAsync(notification.RecipientId, context.CancellationToken);
        var channelPref = pref?.Preferences.GetValueOrDefault(notification.Type);

        if (channelPref?.InApp != false)
        {
            await _notificationRepository.CreateAsync(notification, context.CancellationToken);
            var response = new NotificationResponse(
                notification.Id, notification.Type, notification.Title, notification.Body,
                notification.Data, notification.IsRead, notification.CreatedAt);
            await _hubService.SendNotificationAsync(notification.RecipientId, response, context.CancellationToken);

            var count = await _notificationRepository.GetUnreadCountAsync(notification.RecipientId, context.CancellationToken);
            await _hubService.SendUnreadCountAsync(notification.RecipientId, count, context.CancellationToken);
        }

        if (channelPref?.Push != false)
        {
            var devices = await _deviceTokenRepository.GetByUserIdAsync(notification.RecipientId, context.CancellationToken);
            if (devices.Count > 0)
                await _pushService.SendAsync(devices.Select(d => d.Token).ToList(), notification.Title, notification.Body, notification.Data, context.CancellationToken);
        }
    }
}
