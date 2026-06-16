using Dumble.ChatService.Domain.Models;

namespace Dumble.ChatService.Application.Contracts;

public interface IConversationRepository
{
    Task<Conversation?> GetByIdAsync(string id, CancellationToken ct = default);
    Task<Conversation?> GetDirectConversationAsync(string userId1, string userId2, CancellationToken ct = default);
    Task<List<Conversation>> GetByUserIdAsync(string userId, DateTime? cursor, int limit, CancellationToken ct = default);
    Task CreateAsync(Conversation conversation, CancellationToken ct = default);
    Task UpdateAsync(Conversation conversation, CancellationToken ct = default);
    Task UpdateLastMessageAsync(string conversationId, LastMessageInfo lastMessage, CancellationToken ct = default);
    Task AddParticipantAsync(string conversationId, Participant participant, CancellationToken ct = default);
    Task RemoveParticipantAsync(string conversationId, string userId, CancellationToken ct = default);
    Task UpdateLastReadAsync(string conversationId, string userId, string messageId, CancellationToken ct = default);

    /// <summary>Remove a user from every conversation they belong to — right-to-be-forgotten.</summary>
    Task RemoveParticipantEverywhereAsync(string userId, CancellationToken ct = default);
}
