using Microsoft.AspNetCore.SignalR;

namespace Dumble.NotificationService.API.Hubs;

public class NameUserIdProvider : IUserIdProvider
{
    public string? GetUserId(HubConnectionContext connection)
    {
        return connection.User?.FindFirst("userId")?.Value;
    }
}
