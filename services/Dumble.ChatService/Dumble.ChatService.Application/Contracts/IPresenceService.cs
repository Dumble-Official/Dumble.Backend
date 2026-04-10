namespace Dumble.ChatService.Application.Contracts;

public interface IPresenceService
{
    Task SetOnlineAsync(string userId, CancellationToken ct = default);
    Task SetOfflineAsync(string userId, CancellationToken ct = default);
    Task<bool> IsOnlineAsync(string userId, CancellationToken ct = default);
    Task<Dictionary<string, bool>> GetBatchOnlineStatusAsync(List<string> userIds, CancellationToken ct = default);
    Task SetTypingAsync(string conversationId, string userId, CancellationToken ct = default);
}
