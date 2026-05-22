using Dumble.ChatService.Contracts.Messages;

namespace Dumble.ChatService.Application.Contracts;

public interface IChatHubService
{
    Task SendMessageAsync(string conversationId, MessageResponse message, CancellationToken ct = default);
    Task NotifyMessageDeletedAsync(string conversationId, string messageId, CancellationToken ct = default);
    Task NotifyMessageEditedAsync(string conversationId, string messageId, string newContent, CancellationToken ct = default);
    Task NotifyUserTypingAsync(string conversationId, string userId, string displayName, CancellationToken ct = default);
    Task NotifyConversationUpdatedAsync(string conversationId, object update, CancellationToken ct = default);
    Task NotifyRemovedFromConversationAsync(string targetUserId, string conversationId, CancellationToken ct = default);
}
