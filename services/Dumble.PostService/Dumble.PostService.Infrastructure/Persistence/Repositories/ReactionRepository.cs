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
        var groups = await _context.Reactions
            .Where(r => r.PostId == postId)
            .GroupBy(r => r.Type)
            .ToListAsync(ct);
        return groups.ToDictionary(g => g.Key.ToString()!, g => g.Count());
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
}
