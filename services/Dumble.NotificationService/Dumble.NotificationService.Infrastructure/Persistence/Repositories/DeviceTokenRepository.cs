using MongoDB.Driver;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;

namespace Dumble.NotificationService.Infrastructure.Persistence.Repositories;

public class DeviceTokenRepository : IDeviceTokenRepository
{
    private readonly MongoDbContext _context;

    public DeviceTokenRepository(MongoDbContext context)
    {
        _context = context;
    }

    public async Task<List<DeviceToken>> GetByUserIdAsync(string userId, CancellationToken ct)
    {
        return await _context.DeviceTokens
            .Find(d => d.UserId == userId)
            .ToListAsync(ct);
    }

    public async Task UpsertAsync(DeviceToken deviceToken, CancellationToken ct)
    {
        var filter = Builders<DeviceToken>.Filter.Eq(d => d.UserId, deviceToken.UserId)
            & Builders<DeviceToken>.Filter.Eq(d => d.Token, deviceToken.Token);

        var update = Builders<DeviceToken>.Update
            .Set(d => d.Platform, deviceToken.Platform)
            .Set(d => d.UpdatedAt, DateTime.UtcNow)
            .SetOnInsert(d => d.CreatedAt, DateTime.UtcNow)
            .SetOnInsert(d => d.UserId, deviceToken.UserId)
            .SetOnInsert(d => d.Token, deviceToken.Token);

        await _context.DeviceTokens.UpdateOneAsync(filter, update, new UpdateOptions { IsUpsert = true }, ct);
    }

    public async Task DeleteByTokenAsync(string token, CancellationToken ct)
    {
        await _context.DeviceTokens.DeleteOneAsync(d => d.Token == token, ct);
    }

    public async Task<bool> DeleteOwnedByTokenAsync(string token, string userId, CancellationToken ct)
    {
        var result = await _context.DeviceTokens.DeleteOneAsync(
            d => d.Token == token && d.UserId == userId, ct);
        return result.DeletedCount > 0;
    }

    public Task DeleteAllForUserAsync(string userId, CancellationToken ct)
        => _context.DeviceTokens.DeleteManyAsync(d => d.UserId == userId, ct);
}
