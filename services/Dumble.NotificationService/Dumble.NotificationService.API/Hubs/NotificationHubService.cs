using Microsoft.AspNetCore.SignalR;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Contracts.Notifications;

namespace Dumble.NotificationService.API.Hubs;

public class NotificationHubService : INotificationHubService
{
    private readonly IHubContext<NotificationHub> _hubContext;

    public NotificationHubService(IHubContext<NotificationHub> hubContext)
    {
        _hubContext = hubContext;
    }

    public async Task SendNotificationAsync(string recipientId, NotificationResponse notification, CancellationToken ct = default)
    {
        await _hubContext.Clients.Group(recipientId).SendAsync("ReceiveNotification", notification, ct);
    }

    public async Task SendUnreadCountAsync(string recipientId, int count, CancellationToken ct = default)
    {
        await _hubContext.Clients.Group(recipientId).SendAsync("UnreadCountUpdated", new { count }, ct);
    }
}
