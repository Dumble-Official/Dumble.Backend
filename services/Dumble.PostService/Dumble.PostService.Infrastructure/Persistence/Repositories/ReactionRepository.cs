using Microsoft.EntityFrameworkCore;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Domain.Entities;

namespace Dumble.PostService.Infrastructure.Persistence.Repositories;

public class ReactionRepository : IReactionRepository
{
    private readonly PostDbContext _context;

    public ReactionRepository(PostDbContext context)
    {
        _context = context;
    }

    public async Task<Reaction?> GetByPostAndUserAsync(Guid postId, string userId, CancellationToken ct)
    {
        return await _context.Reactions
            .FirstOrDefaultAsync(r => r.PostId == postId && r.UserId == userId, ct);
    }

    public async Task<List<Reaction>> GetByPostIdAsync(Guid postId, int offset, int limit, CancellationToken ct)
    {
        return await _context.Reactions
            .Where(r => r.PostId == postId)
            .OrderByDescending(r => r.CreatedAt)
            .Skip(offset)
            .Take(limit)
            .ToListAsync(ct);
    }

    public async Task<Dictionary<string, int>> GetCountsByPostIdAsync(Guid postId, CancellationToken ct)
    {
        // Project the aggregate inside the query — EF can translate GroupBy+Select
        // to SQL, but materializing the IGrouping itself (GroupBy then ToList) is
        // not translatable and throws at runtime.
        var counts = await _context.Reactions
            .Where(r => r.PostId == postId)
            .GroupBy(r => r.Type)
            .Select(g => new { Type = g.Key, Count = g.Count() })
            .ToListAsync(ct);
        return counts.ToDictionary(x => x.Type.ToString()!, x => x.Count);
    }

    public async Task<Reaction> CreateAsync(Reaction reaction, CancellationToken ct)
    {
        _context.Reactions.Add(reaction);
        await _context.SaveChangesAsync(ct);
        return reaction;
    }

    public async Task UpdateAsync(Reaction reaction, CancellationToken ct)
    {
        _context.Reactions.Update(reaction);
        await _context.SaveChangesAsync(ct);
    }

    public async Task DeleteAsync(Reaction reaction, CancellationToken ct)
    {
        _context.Reactions.Remove(reaction);
        await _context.SaveChangesAsync(ct);
    }

    public Task<int> DeleteAllByUserAsync(string userId, CancellationToken ct = default) =>
        _context.Reactions.Where(r => r.UserId == userId).ExecuteDeleteAsync(ct);
}
