using MassTransit;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;
using Dumble.SharedKernel.Events.Chat;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers;

public class MessageSentConsumer : IConsumer<MessageSentEvent>
{
    private readonly IDeviceTokenRepository _deviceTokenRepository;
    private readonly INotificationPreferenceRepository _preferenceRepository;
    private readonly IPushNotificationService _pushService;

    public MessageSentConsumer(
        IDeviceTokenRepository deviceTokenRepository,
        INotificationPreferenceRepository preferenceRepository,
        IPushNotificationService pushService)
    {
        _deviceTokenRepository = deviceTokenRepository;
        _preferenceRepository = preferenceRepository;
        _pushService = pushService;
    }

    public async Task Consume(ConsumeContext<MessageSentEvent> context)
    {
        var evt = context.Message;

        // Push-only for chat messages (no in-app notification stored)
        foreach (var recipientId in evt.RecipientIds)
        {
            if (recipientId == evt.SenderId) continue;

            var pref = await _preferenceRepository.GetByUserIdAsync(recipientId, context.CancellationToken);
            var channelPref = pref?.Preferences.GetValueOrDefault("ChatMessage");

            if (channelPref?.Push != false)
            {
                var devices = await _deviceTokenRepository.GetByUserIdAsync(recipientId, context.CancellationToken);
                if (devices.Count > 0)
                {
                    await _pushService.SendAsync(
                        devices.Select(d => d.Token).ToList(),
                        evt.SenderName,
                        evt.Preview,
                        new Dictionary<string, string>
                        {
                            ["conversationId"] = evt.ConversationId,
                            ["senderId"] = evt.SenderId,
                            ["senderName"] = evt.SenderName
                        },
                        context.CancellationToken);
                }
            }
        }
    }
}
