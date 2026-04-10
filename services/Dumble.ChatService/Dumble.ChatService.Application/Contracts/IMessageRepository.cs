using Dumble.ChatService.Domain.Models;

namespace Dumble.ChatService.Application.Contracts;

public interface IMessageRepository
{
    Task<Message?> GetByIdAsync(string id, CancellationToken ct = default);
    Task<List<Message>> GetByConversationIdAsync(string conversationId, DateTime? cursor, int limit, CancellationToken ct = default);
    Task CreateAsync(Message message, CancellationToken ct = default);
    Task SoftDeleteAsync(string messageId, CancellationToken ct = default);
}
