using Dumble.NotificationService.Application.Contracts;
using Dumble.SharedKernel.Events.Accounts;
using MassTransit;
using Microsoft.Extensions.Logging;

namespace Dumble.NotificationService.Infrastructure.Messaging.Consumers;

/// <summary>
/// Right-to-be-forgotten: on account deletion, purge everything the notification service holds for
/// the user — their device tokens (otherwise valid push endpoints for a deleted account live on),
/// their stored notifications, and their preference document.
/// </summary>
public sealed class AccountDeletedConsumer : IConsumer<AccountDeletedEvent>
{
    private readonly IDeviceTokenRepository _deviceTokens;
    private readonly INotificationRepository _notifications;
    private readonly INotificationPreferenceRepository _preferences;
    private readonly ILogger<AccountDeletedConsumer> _logger;

    public AccountDeletedConsumer(
        IDeviceTokenRepository deviceTokens,
        INotificationRepository notifications,
        INotificationPreferenceRepository preferences,
        ILogger<AccountDeletedConsumer> logger)
    {
        _deviceTokens = deviceTokens;
        _notifications = notifications;
        _preferences = preferences;
        _logger = logger;
    }

    public async Task Consume(ConsumeContext<AccountDeletedEvent> context)
    {
        var userId = context.Message.UserId;
        if (string.IsNullOrWhiteSpace(userId))
        {
            _logger.LogWarning("Account-deleted event carried no userId; nothing to forget");
            return;
        }

        var ct = context.CancellationToken;
        await _deviceTokens.DeleteAllForUserAsync(userId, ct);
        await _notifications.DeleteAllForRecipientAsync(userId, ct);
        await _preferences.DeleteForUserAsync(userId, ct);

        _logger.LogInformation("Forgot user {UserId}: purged device tokens, notifications and preferences", userId);
    }
}
