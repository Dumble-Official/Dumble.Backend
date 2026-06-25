namespace Dumble.ChatService.Application.Contracts;

public interface IPresenceService
{
    /// <summary>Registers a live connection for the user (also used as a heartbeat
    /// to refresh the TTL). Returns true only when this is the user's FIRST live
    /// connection, i.e. they just transitioned offline→online.</summary>
    Task<bool> SetOnlineAsync(string userId, string connectionId, CancellationToken ct = default);

    /// <summary>Removes a connection for the user. Returns true only when this was
    /// the user's LAST live connection, i.e. they just transitioned online→offline.
    /// While any other connection remains, the user stays online.</summary>
    Task<bool> SetOfflineAsync(string userId, string connectionId, CancellationToken ct = default);

    Task<bool> IsOnlineAsync(string userId, CancellationToken ct = default);
    Task<Dictionary<string, bool>> GetBatchOnlineStatusAsync(List<string> userIds, CancellationToken ct = default);
    Task SetTypingAsync(string conversationId, string userId, CancellationToken ct = default);
}
