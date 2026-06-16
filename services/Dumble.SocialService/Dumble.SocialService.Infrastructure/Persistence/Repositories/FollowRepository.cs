using Microsoft.EntityFrameworkCore;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Domain.Entities;

namespace Dumble.SocialService.Infrastructure.Persistence.Repositories;

public class FollowRepository : IFollowRepository
{
    private readonly SocialDbContext _db;

    public FollowRepository(SocialDbContext db)
    {
        _db = db;
    }

    public async Task<Follow?> GetAsync(string followerId, string followeeId, CancellationToken ct = default)
    {
        return await _db.Follows
            .FirstOrDefaultAsync(f => f.FollowerId == followerId && f.FolloweeId == followeeId, ct);
    }

    public async Task CreateAsync(Follow follow, CancellationToken ct = default)
    {
        _db.Follows.Add(follow);
        await _db.SaveChangesAsync(ct);
    }

    public async Task<bool> DeleteAsync(string followerId, string followeeId, CancellationToken ct = default)
    {
        var rows = await _db.Follows
            .Where(f => f.FollowerId == followerId && f.FolloweeId == followeeId)
            .ExecuteDeleteAsync(ct);
        return rows > 0;
    }

    public Task<int> DeleteAllForUserAsync(string userId, CancellationToken ct = default)
    {
        // Both directions: edges the user created and edges pointing at them.
        return _db.Follows
            .Where(f => f.FollowerId == userId || f.FolloweeId == userId)
            .ExecuteDeleteAsync(ct);
    }

    public async Task<List<Follow>> GetFollowersAsync(string userId, DateTime? cursor, int limit, CancellationToken ct = default)
    {
        IQueryable<Follow> query = _db.Follows.Where(f => f.FolloweeId == userId);

        if (cursor.HasValue)
            query = query.Where(f => f.CreatedAt < cursor.Value);

        return await query
            .OrderByDescending(f => f.CreatedAt)
            .Take(limit)
            .ToListAsync(ct);
    }

    public async Task<List<Follow>> GetFollowingAsync(string userId, DateTime? cursor, int limit, CancellationToken ct = default)
    {
        IQueryable<Follow> query = _db.Follows.Where(f => f.FollowerId == userId);

        if (cursor.HasValue)
            query = query.Where(f => f.CreatedAt < cursor.Value);

        return await query
            .OrderByDescending(f => f.CreatedAt)
            .Take(limit)
            .ToListAsync(ct);
    }

    public async Task<int> GetFollowersCountAsync(string userId, CancellationToken ct = default)
    {
        return await _db.Follows.CountAsync(f => f.FolloweeId == userId, ct);
    }

    public async Task<int> GetFollowingCountAsync(string userId, CancellationToken ct = default)
    {
        return await _db.Follows.CountAsync(f => f.FollowerId == userId, ct);
    }

    public async Task<Dictionary<string, bool>> GetFollowStatusBatchAsync(string followerId, List<string> followeeIds, CancellationToken ct = default)
    {
        var followedIds = await _db.Follows
            .Where(f => f.FollowerId == followerId && followeeIds.Contains(f.FolloweeId))
            .Select(f => f.FolloweeId)
            .ToListAsync(ct);

        return followeeIds.ToDictionary(id => id, id => followedIds.Contains(id));
    }

    public async Task<List<string>> GetFolloweeIdsAsync(string userId, CancellationToken ct = default)
    {
        return await _db.Follows
            .Where(f => f.FollowerId == userId)
            .Select(f => f.FolloweeId)
            .ToListAsync(ct);
    }
}
