using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.SignalR;

namespace Dumble.NotificationService.API.Hubs;

[Authorize]
public class NotificationHub : Hub
{
    public override async Task OnConnectedAsync()
    {
        // The platform issues a short-lived JWT with purpose=hub specifically
        // for SignalR connections — reject any token that doesn't carry the
        // marker so leaked API tokens can't pivot into real-time channels.
        var purpose = Context.User?.FindFirst("purpose")?.Value;
        if (!string.Equals(purpose, "hub", StringComparison.Ordinal))
        {
            throw new HubException("Hub connections require a purpose=hub token (POST /api/auth/hub-token)");
        }

        var userId = Context.UserIdentifier;
        if (userId is not null)
            await Groups.AddToGroupAsync(Context.ConnectionId, userId);

        await base.OnConnectedAsync();
    }

    public override async Task OnDisconnectedAsync(Exception? exception)
    {
        var userId = Context.UserIdentifier;
        if (userId is not null)
            await Groups.RemoveFromGroupAsync(Context.ConnectionId, userId);

        await base.OnDisconnectedAsync(exception);
    }
}
