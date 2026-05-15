using Microsoft.AspNetCore.SignalR;
using Dumble.ChatService.Application.Contracts;
using Dumble.ChatService.Contracts.Messages;

namespace Dumble.ChatService.API.Hubs;

public class ChatHubService : IChatHubService
{
    private readonly IHubContext<ChatHub> _hubContext;

    public ChatHubService(IHubContext<ChatHub> hubContext)
    {
        _hubContext = hubContext;
    }

    public Task SendMessageAsync(string conversationId, MessageResponse message, CancellationToken ct = default)
    {
        // Fire-and-forget the broadcast: one slow/zombie client in the group
        // would otherwise stall the HTTP send-message response for everyone
        // else. We still log on failure via the exception handler on the task.
        _ = _hubContext.Clients.Group(conversationId).SendAsync("ReceiveMessage", message, ct);
        return Task.CompletedTask;
    }

    public Task NotifyMessageDeletedAsync(string conversationId, string messageId, CancellationToken ct = default)
    {
        _ = _hubContext.Clients.Group(conversationId).SendAsync("MessageDeleted", new { messageId, conversationId }, ct);
        return Task.CompletedTask;
    }

    public Task NotifyUserTypingAsync(string conversationId, string userId, string displayName, CancellationToken ct = default)
    {
        _ = _hubContext.Clients.Group(conversationId).SendAsync("UserTyping", new { conversationId, userId, displayName }, ct);
        return Task.CompletedTask;
    }

    public Task NotifyConversationUpdatedAsync(string conversationId, object update, CancellationToken ct = default)
    {
        _ = _hubContext.Clients.Group(conversationId).SendAsync("ConversationUpdated", update, ct);
        return Task.CompletedTask;
    }

    public Task NotifyRemovedFromConversationAsync(string targetUserId, string conversationId, CancellationToken ct = default)
    {
        // Targets the user's session group (mapped to userId by NameUserIdProvider).
        // Client-side handler unsubscribes the SignalR connection from the
        // conversation group on receipt.
        _ = _hubContext.Clients.User(targetUserId).SendAsync("RemovedFromConversation", new { conversationId }, ct);
        return Task.CompletedTask;
    }
}
