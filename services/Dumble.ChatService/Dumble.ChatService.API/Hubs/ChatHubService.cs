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

    public async Task SendMessageAsync(string conversationId, MessageResponse message, CancellationToken ct = default)
    {
        await _hubContext.Clients.Group(conversationId).SendAsync("ReceiveMessage", message, ct);
    }

    public async Task NotifyMessageDeletedAsync(string conversationId, string messageId, CancellationToken ct = default)
    {
        await _hubContext.Clients.Group(conversationId).SendAsync("MessageDeleted", new { messageId, conversationId }, ct);
    }

    public async Task NotifyUserTypingAsync(string conversationId, string userId, string displayName, CancellationToken ct = default)
    {
        await _hubContext.Clients.Group(conversationId).SendAsync("UserTyping", new { conversationId, userId, displayName }, ct);
    }

    public async Task NotifyConversationUpdatedAsync(string conversationId, object update, CancellationToken ct = default)
    {
        await _hubContext.Clients.Group(conversationId).SendAsync("ConversationUpdated", update, ct);
    }
}
