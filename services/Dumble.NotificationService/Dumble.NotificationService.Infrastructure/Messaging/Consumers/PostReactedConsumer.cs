using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Contracts.Notifications;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Posts;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers;

public class PostReactedConsumer : IConsumer<PostReactedEvent>
{
    private readonly INotificationRepository _notificationRepository;
    private readonly INotificationPreferenceRepository _preferenceRepository;
    private readonly IDeviceTokenRepository _deviceTokenRepository;
    private readonly IPushNotificationService _pushService;
    private readonly INotificationHubService _hubService;

    public PostReactedConsumer(
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

    public async Task Consume(ConsumeContext<PostReactedEvent> context)
    {
        var evt = context.Message;
        if (evt.PostAuthorId == evt.ReactorId) return; // Don't notify self

        var notification = new Notification
        {
            RecipientId = evt.PostAuthorId,
            Type = "PostReaction",
            Title = "New Reaction",
            Body = $"{evt.ReactorName} reacted {evt.ReactionType} to your post",
            Data = new Dictionary<string, string>
            {
                ["postId"] = evt.PostId,
                ["reactorId"] = evt.ReactorId,
                ["reactorName"] = evt.ReactorName,
                ["reactionType"] = evt.ReactionType.ToString()
            },
            CreatedAt = DateTime.UtcNow,
            ExpiresAt = DateTime.UtcNow.AddDays(30)
        };

        await DeliverNotificationAsync(notification, context.CancellationToken);
    }

    private async Task DeliverNotificationAsync(Notification notification, CancellationToken ct)
    {
        var pref = await _preferenceRepository.GetByUserIdAsync(notification.RecipientId, ct);
        var channelPref = pref?.Preferences.GetValueOrDefault(notification.Type);

        if (channelPref?.InApp != false)
        {
            await _notificationRepository.CreateAsync(notification, ct);
            var response = new NotificationResponse(
                notification.Id, notification.Type, notification.Title, notification.Body,
                notification.Data, notification.IsRead, notification.CreatedAt);
            await _hubService.SendNotificationAsync(notification.RecipientId, response, ct);

            var count = await _notificationRepository.GetUnreadCountAsync(notification.RecipientId, ct);
            await _hubService.SendUnreadCountAsync(notification.RecipientId, count, ct);
        }

        if (channelPref?.Push != false)
        {
            var devices = await _deviceTokenRepository.GetByUserIdAsync(notification.RecipientId, ct);
            if (devices.Count > 0)
            {
                await _pushService.SendAsync(
                    devices.Select(d => d.Token).ToList(),
                    notification.Title, notification.Body, notification.Data, ct);
            }
        }
    }
}
