using MongoDB.Driver;
using Dumble.ChatService.Application.Contracts;
using Dumble.ChatService.Domain.Models;

namespace Dumble.ChatService.Infrastructure.Persistence.Repositories;

public class MessageRepository : IMessageRepository
{
    private readonly MongoDbContext _context;

    public MessageRepository(MongoDbContext context)
    {
        _context = context;
    }

    public async Task<Message?> GetByIdAsync(string id, CancellationToken ct = default)
    {
        return await _context.Messages.Find(m => m.Id == id).FirstOrDefaultAsync(ct);
    }

    public async Task<List<Message>> GetByConversationIdAsync(string conversationId, DateTime? cursor, int limit, CancellationToken ct = default)
    {
        // Exclude soft-deleted messages — SoftDeleteAsync flips IsDeleted=true
        // and rewrites Content; without this filter the deleted row keeps
        // surfacing in conversation history until pagination scrolls past it.
        var filter = Builders<Message>.Filter.And(
            Builders<Message>.Filter.Eq(m => m.ConversationId, conversationId),
            Builders<Message>.Filter.Eq(m => m.IsDeleted, false));

        if (cursor.HasValue)
            filter = Builders<Message>.Filter.And(filter,
                Builders<Message>.Filter.Lt(m => m.CreatedAt, cursor.Value));

        return await _context.Messages
            .Find(filter)
            .SortByDescending(m => m.CreatedAt)
            .Limit(limit)
            .ToListAsync(ct);
    }

    public async Task CreateAsync(Message message, CancellationToken ct = default)
    {
        await _context.Messages.InsertOneAsync(message, cancellationToken: ct);
    }

    public async Task SoftDeleteAsync(string messageId, CancellationToken ct = default)
    {
        var update = Builders<Message>.Update
            .Set(m => m.IsDeleted, true)
            .Set(m => m.Content, "This message was deleted");

        await _context.Messages.UpdateOneAsync(m => m.Id == messageId, update, cancellationToken: ct);
    }

    public async Task EditAsync(string messageId, string newContent, CancellationToken ct = default)
    {
        var update = Builders<Message>.Update
            .Set(m => m.Content, newContent)
            .Set(m => m.IsEdited, true)
            .Set(m => m.EditedAt, DateTime.UtcNow);

        await _context.Messages.UpdateOneAsync(m => m.Id == messageId, update, cancellationToken: ct);
    }

    public Task AnonymizeSenderAsync(string userId, CancellationToken ct = default)
    {
        // Right-to-be-forgotten: strip the sender's identity from every message they authored.
        // Bodies are kept so the conversation stays readable for the other participants.
        var update = Builders<Message>.Update
            .Set(m => m.SenderName, "[deleted user]")
            .Set(m => m.SenderProfileImage, (string?)null);

        return _context.Messages.UpdateManyAsync(m => m.SenderId == userId, update, cancellationToken: ct);
    }
}
