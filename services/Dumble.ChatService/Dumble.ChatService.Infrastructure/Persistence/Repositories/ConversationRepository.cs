using MongoDB.Driver;
using Dumble.ChatService.Application.Contracts;
using Dumble.ChatService.Domain.Models;

namespace Dumble.ChatService.Infrastructure.Persistence.Repositories;

public class ConversationRepository : IConversationRepository
{
    private readonly MongoDbContext _context;

    public ConversationRepository(MongoDbContext context)
    {
        _context = context;
    }

    public async Task<Conversation?> GetByIdAsync(string id, CancellationToken ct = default)
    {
        return await _context.Conversations.Find(c => c.Id == id).FirstOrDefaultAsync(ct);
    }

    public async Task<Conversation?> GetDirectConversationAsync(string userId1, string userId2, CancellationToken ct = default)
    {
        var filter = Builders<Conversation>.Filter.And(
            Builders<Conversation>.Filter.Eq(c => c.Type, "Direct"),
            Builders<Conversation>.Filter.ElemMatch(c => c.Participants, p => p.UserId == userId1),
            Builders<Conversation>.Filter.ElemMatch(c => c.Participants, p => p.UserId == userId2));

        return await _context.Conversations.Find(filter).FirstOrDefaultAsync(ct);
    }

    public async Task<List<Conversation>> GetByUserIdAsync(string userId, DateTime? cursor, int limit, CancellationToken ct = default)
    {
        var filter = Builders<Conversation>.Filter.ElemMatch(c => c.Participants, p => p.UserId == userId);

        if (cursor.HasValue)
            filter = Builders<Conversation>.Filter.And(filter,
                Builders<Conversation>.Filter.Lt(c => c.UpdatedAt, cursor.Value));

        return await _context.Conversations
            .Find(filter)
            .SortByDescending(c => c.UpdatedAt)
            .Limit(limit)
            .ToListAsync(ct);
    }

    public async Task CreateAsync(Conversation conversation, CancellationToken ct = default)
    {
        await _context.Conversations.InsertOneAsync(conversation, cancellationToken: ct);
    }

    public async Task UpdateAsync(Conversation conversation, CancellationToken ct = default)
    {
        await _context.Conversations.ReplaceOneAsync(c => c.Id == conversation.Id, conversation, cancellationToken: ct);
    }

    public async Task UpdateLastMessageAsync(string conversationId, LastMessageInfo lastMessage, CancellationToken ct = default)
    {
        var update = Builders<Conversation>.Update
            .Set(c => c.LastMessage, lastMessage)
            .Set(c => c.UpdatedAt, DateTime.UtcNow);

        await _context.Conversations.UpdateOneAsync(c => c.Id == conversationId, update, cancellationToken: ct);
    }

    public async Task AddParticipantAsync(string conversationId, Participant participant, CancellationToken ct = default)
    {
        var update = Builders<Conversation>.Update
            .Push(c => c.Participants, participant)
            .Set(c => c.UpdatedAt, DateTime.UtcNow);

        await _context.Conversations.UpdateOneAsync(c => c.Id == conversationId, update, cancellationToken: ct);
    }

    public Task RemoveParticipantEverywhereAsync(string userId, CancellationToken ct = default)
    {
        // Right-to-be-forgotten: drop the user from every conversation's participant list so they
        // no longer appear in any member roster.
        var update = Builders<Conversation>.Update
            .PullFilter(c => c.Participants, p => p.UserId == userId)
            .Set(c => c.UpdatedAt, DateTime.UtcNow);

        return _context.Conversations.UpdateManyAsync(
            c => c.Participants.Any(p => p.UserId == userId), update, cancellationToken: ct);
    }

    public async Task RemoveParticipantAsync(string conversationId, string userId, CancellationToken ct = default)
    {
        var update = Builders<Conversation>.Update
            .PullFilter(c => c.Participants, p => p.UserId == userId)
            .Set(c => c.UpdatedAt, DateTime.UtcNow);

        await _context.Conversations.UpdateOneAsync(c => c.Id == conversationId, update, cancellationToken: ct);
    }

    public async Task UpdateLastReadAsync(string conversationId, string userId, string messageId, CancellationToken ct = default)
    {
        var filter = Builders<Conversation>.Filter.And(
            Builders<Conversation>.Filter.Eq(c => c.Id, conversationId),
            Builders<Conversation>.Filter.ElemMatch(c => c.Participants, p => p.UserId == userId));

        var update = Builders<Conversation>.Update
            .Set("Participants.$.LastReadMessageId", messageId);

        await _context.Conversations.UpdateOneAsync(filter, update, cancellationToken: ct);
    }
}
