using MongoDB.Driver;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;

namespace Dumble.NotificationService.Infrastructure.Persistence.Repositories;

public class NotificationPreferenceRepository : INotificationPreferenceRepository
{
    private readonly MongoDbContext _context;

    public NotificationPreferenceRepository(MongoDbContext context)
    {
        _context = context;
    }

    public async Task<NotificationPreference?> GetByUserIdAsync(string userId, CancellationToken ct)
    {
        return await _context.NotificationPreferences
            .Find(p => p.UserId == userId)
            .FirstOrDefaultAsync(ct);
    }

    public async Task UpsertAsync(NotificationPreference preference, CancellationToken ct)
    {
        var filter = Builders<NotificationPreference>.Filter.Eq(p => p.UserId, preference.UserId);
        var options = new ReplaceOptions { IsUpsert = true };
        await _context.NotificationPreferences.ReplaceOneAsync(filter, preference, options, ct);
    }

    public Task DeleteForUserAsync(string userId, CancellationToken ct)
        => _context.NotificationPreferences.DeleteOneAsync(p => p.UserId == userId, ct);
}
