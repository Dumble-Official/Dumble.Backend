using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Contracts.Notifications;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Posts;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers;

public class CommentCreatedConsumer : IConsumer<CommentCreatedEvent>
{
    private readonly INotificationRepository _notificationRepository;
    private readonly INotificationPreferenceRepository _preferenceRepository;
    private readonly IDeviceTokenRepository _deviceTokenRepository;
    private readonly IPushNotificationService _pushService;
    private readonly INotificationHubService _hubService;

    public CommentCreatedConsumer(
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

    public async Task Consume(ConsumeContext<CommentCreatedEvent> context)
    {
        var evt = context.Message;

        // Notify post author
        if (evt.PostAuthorId != evt.CommentAuthorId)
        {
            await DeliverAsync(new Notification
            {
                RecipientId = evt.PostAuthorId,
                Type = "Comment",
                Title = "New Comment",
                Body = $"{evt.CommenterName} commented on your post: {evt.Preview}",
                Data = new Dictionary<string, string>
                {
                    ["postId"] = evt.PostId,
                    ["commentId"] = evt.CommentId,
                    ["commenterName"] = evt.CommenterName,
                    ["preview"] = evt.Preview
                },
                CreatedAt = DateTime.UtcNow,
                ExpiresAt = DateTime.UtcNow.AddDays(30)
            }, context.CancellationToken);
        }

        // Notify parent comment author (for replies)
        if (evt.ParentCommentAuthorId is not null && evt.ParentCommentAuthorId != evt.CommentAuthorId)
        {
            await DeliverAsync(new Notification
            {
                RecipientId = evt.ParentCommentAuthorId,
                Type = "Comment",
                Title = "New Reply",
                Body = $"{evt.CommenterName} replied to your comment: {evt.Preview}",
                Data = new Dictionary<string, string>
                {
                    ["postId"] = evt.PostId,
                    ["commentId"] = evt.CommentId,
                    ["commenterName"] = evt.CommenterName,
                    ["preview"] = evt.Preview
                },
                CreatedAt = DateTime.UtcNow,
                ExpiresAt = DateTime.UtcNow.AddDays(30)
            }, context.CancellationToken);
        }
    }

    private async Task DeliverAsync(Notification notification, CancellationToken ct)
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
                await _pushService.SendAsync(devices.Select(d => d.Token).ToList(), notification.Title, notification.Body, notification.Data, ct);
        }
    }
}
