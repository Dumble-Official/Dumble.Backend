using MongoDB.Driver;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;

namespace Dumble.NotificationService.Infrastructure.Persistence.Repositories;

public class NotificationRepository : INotificationRepository
{
    private readonly MongoDbContext _context;

    public NotificationRepository(MongoDbContext context)
    {
        _context = context;
    }

    public async Task<Notification?> GetByIdAsync(string id, CancellationToken ct)
    {
        return await _context.Notifications
            .Find(n => n.Id == id)
            .FirstOrDefaultAsync(ct);
    }

    public async Task<List<Notification>> GetByRecipientAsync(string recipientId, DateTime? cursor, int limit, CancellationToken ct)
    {
        var filter = Builders<Notification>.Filter.Eq(n => n.RecipientId, recipientId);

        if (cursor.HasValue)
            filter &= Builders<Notification>.Filter.Lt(n => n.CreatedAt, cursor.Value);

        return await _context.Notifications
            .Find(filter)
            .SortByDescending(n => n.CreatedAt)
            .Limit(limit)
            .ToListAsync(ct);
    }

    public async Task<int> GetUnreadCountAsync(string recipientId, CancellationToken ct)
    {
        var filter = Builders<Notification>.Filter.Eq(n => n.RecipientId, recipientId)
            & Builders<Notification>.Filter.Eq(n => n.IsRead, false);

        return (int)await _context.Notifications.CountDocumentsAsync(filter, cancellationToken: ct);
    }

    public async Task CreateAsync(Notification notification, CancellationToken ct)
    {
        await _context.Notifications.InsertOneAsync(notification, cancellationToken: ct);
    }

    public async Task MarkAsReadAsync(string id, CancellationToken ct)
    {
        // Conditional set — only flip false→true so two concurrent retries
        // can't double-decrement an unread counter computed off the result.
        var filter = Builders<Notification>.Filter.And(
            Builders<Notification>.Filter.Eq(n => n.Id, id),
            Builders<Notification>.Filter.Eq(n => n.IsRead, false));
        var update = Builders<Notification>.Update.Set(n => n.IsRead, true);
        await _context.Notifications.UpdateOneAsync(filter, update, cancellationToken: ct);
    }

    public async Task MarkAllAsReadAsync(string recipientId, CancellationToken ct)
    {
        var filter = Builders<Notification>.Filter.Eq(n => n.RecipientId, recipientId)
            & Builders<Notification>.Filter.Eq(n => n.IsRead, false);
        var update = Builders<Notification>.Update.Set(n => n.IsRead, true);

        await _context.Notifications.UpdateManyAsync(filter, update, cancellationToken: ct);
    }

    public async Task DeleteAsync(string id, CancellationToken ct)
    {
        await _context.Notifications.DeleteOneAsync(n => n.Id == id, ct);
    }
}
