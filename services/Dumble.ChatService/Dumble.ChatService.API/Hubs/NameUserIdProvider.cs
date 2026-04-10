using Microsoft.AspNetCore.SignalR;

namespace Dumble.ChatService.API.Hubs;

public class NameUserIdProvider : IUserIdProvider
{
    public string? GetUserId(HubConnectionContext connection)
    {
        return connection.User?.FindFirst("userId")?.Value;
    }
}
