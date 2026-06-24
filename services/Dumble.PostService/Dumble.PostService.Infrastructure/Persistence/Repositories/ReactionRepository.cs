using Microsoft.EntityFrameworkCore;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Domain.Entities;
using Dumble.PostService.Domain.Enums;

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

    public Task<int> DeleteAllByUserAsync(string userId, CancellationToken ct = default) =>
        _context.Reactions.Where(r => r.UserId == userId).ExecuteDeleteAsync(ct);

    public async Task<List<(Post Post, DateTime ReactedAt)>> GetReactedPostsByUserAsync(
        string userId, DateTime? cursor, int limit, CancellationToken ct)
    {
        // One reaction per (post, user) — the AddReaction flow upserts — so the
        // reaction CreatedAt is a stable per-post key for cursor pagination.
        var query = _context.Reactions
            .Where(r => r.UserId == userId && r.Post.Status != PostStatus.Deleted);

        if (cursor.HasValue)
            query = query.Where(r => r.CreatedAt < cursor.Value);

        var rows = await query
            .OrderByDescending(r => r.CreatedAt)
            .Take(limit)
            .Select(r => new { Reaction = r, r.CreatedAt })
            .ToListAsync(ct);

        var postIds = rows.Select(x => x.Reaction.PostId).ToList();

        var posts = await _context.Posts
            .Include(p => p.Images.OrderBy(i => i.Order))
            .Include(p => p.PostHashtags).ThenInclude(ph => ph.Hashtag)
            .Where(p => postIds.Contains(p.Id))
            .ToListAsync(ct);

        var postsById = posts.ToDictionary(p => p.Id);

        // Preserve reaction-recency order from the cursor query.
        return rows
            .Where(x => postsById.ContainsKey(x.Reaction.PostId))
            .Select(x => (postsById[x.Reaction.PostId], x.CreatedAt))
            .ToList();
    }
}
