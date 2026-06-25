using Dumble.ChatService.Domain.Models;

namespace Dumble.ChatService.Application.Contracts;

public interface IMessageRepository
{
    Task<Message?> GetByIdAsync(string id, CancellationToken ct = default);
    Task<List<Message>> GetByConversationIdAsync(string conversationId, DateTime? cursor, int limit, CancellationToken ct = default);

    /// <summary>Counts messages in a conversation that the requesting user hasn't read:
    /// authored by someone else, not soft-deleted, and newer than
    /// <paramref name="lastReadMessageId"/> (all such messages when it is null).</summary>
    Task<long> CountUnreadAsync(string conversationId, string? lastReadMessageId, string requestingUserId, CancellationToken ct = default);
    Task CreateAsync(Message message, CancellationToken ct = default);
    Task SoftDeleteAsync(string messageId, CancellationToken ct = default);
    Task EditAsync(string messageId, string newContent, CancellationToken ct = default);

    /// <summary>Strip the sender identity from every message a user authored — right-to-be-forgotten.</summary>
    Task AnonymizeSenderAsync(string userId, CancellationToken ct = default);
}
