using Microsoft.EntityFrameworkCore;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Domain.Entities;

namespace Dumble.SocialService.Infrastructure.Persistence.Repositories;

public class UserBehaviorRepository : IUserBehaviorRepository
{
    private readonly SocialDbContext _db;

    public UserBehaviorRepository(SocialDbContext db)
    {
        _db = db;
    }

    public async Task CreateAsync(UserBehavior behavior, CancellationToken ct = default)
    {
        _db.UserBehaviors.Add(behavior);
        await _db.SaveChangesAsync(ct);
    }

    public async Task CreateBatchAsync(List<UserBehavior> behaviors, CancellationToken ct = default)
    {
        _db.UserBehaviors.AddRange(behaviors);
        await _db.SaveChangesAsync(ct);
    }

    public Task<int> DeleteAllForUserAsync(string userId, CancellationToken ct = default)
        => _db.UserBehaviors.Where(b => b.UserId == userId).ExecuteDeleteAsync(ct);

    public async Task<List<UserBehavior>> GetRecentAsync(string userId, int limit, CancellationToken ct = default)
    {
        return await _db.UserBehaviors
            .Where(b => b.UserId == userId)
            .OrderByDescending(b => b.CreatedAt)
            .Take(limit)
            .ToListAsync(ct);
    }
}
