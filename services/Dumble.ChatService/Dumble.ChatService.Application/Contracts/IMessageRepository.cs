using Dumble.ChatService.Domain.Models;

namespace Dumble.ChatService.Application.Contracts;

public interface IMessageRepository
{
    Task<Message?> GetByIdAsync(string id, CancellationToken ct = default);
    Task<List<Message>> GetByConversationIdAsync(string conversationId, DateTime? cursor, int limit, CancellationToken ct = default);
    Task CreateAsync(Message message, CancellationToken ct = default);
    Task SoftDeleteAsync(string messageId, CancellationToken ct = default);
    Task EditAsync(string messageId, string newContent, CancellationToken ct = default);

    /// <summary>Strip the sender identity from every message a user authored — right-to-be-forgotten.</summary>
    Task AnonymizeSenderAsync(string userId, CancellationToken ct = default);
}
