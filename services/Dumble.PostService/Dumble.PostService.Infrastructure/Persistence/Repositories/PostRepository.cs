using Microsoft.EntityFrameworkCore;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Domain.Entities;
using Dumble.PostService.Domain.Enums;

namespace Dumble.PostService.Infrastructure.Persistence.Repositories;

public class PostRepository : IPostRepository
{
    private readonly PostDbContext _context;

    public PostRepository(PostDbContext context)
    {
        _context = context;
    }

    public async Task<Post?> GetByIdAsync(Guid id, CancellationToken ct)
    {
        return await _context.Posts.FirstOrDefaultAsync(p => p.Id == id && p.Status != PostStatus.Deleted, ct);
    }

    public async Task<Post?> GetByIdWithDetailsAsync(Guid id, CancellationToken ct)
    {
        return await _context.Posts
            .Include(p => p.Images.OrderBy(i => i.Order))
            .Include(p => p.PostHashtags).ThenInclude(ph => ph.Hashtag)
            .FirstOrDefaultAsync(p => p.Id == id, ct);
    }

    public async Task<List<Post>> GetByAuthorIdAsync(string authorId, DateTime? cursor, int limit, CancellationToken ct)
    {
        var query = _context.Posts
            .Include(p => p.Images.OrderBy(i => i.Order))
            .Include(p => p.PostHashtags).ThenInclude(ph => ph.Hashtag)
            .Where(p => p.AuthorId == authorId && p.Status != PostStatus.Deleted);

        if (cursor.HasValue)
            query = query.Where(p => p.CreatedAt < cursor.Value);

        return await query
            .OrderByDescending(p => p.CreatedAt)
            .Take(limit)
            .ToListAsync(ct);
    }

    public async Task<List<Post>> GetByGymIdAsync(string gymId, DateTime? cursor, int limit, CancellationToken ct)
    {
        var query = _context.Posts
            .Include(p => p.Images.OrderBy(i => i.Order))
            .Include(p => p.PostHashtags).ThenInclude(ph => ph.Hashtag)
            .Where(p => p.GymId == gymId && p.Status != PostStatus.Deleted);

        if (cursor.HasValue)
            query = query.Where(p => p.CreatedAt < cursor.Value);

        return await query
            .OrderByDescending(p => p.CreatedAt)
            .Take(limit)
            .ToListAsync(ct);
    }

    public async Task<List<Post>> GetByHashtagAsync(string hashtag, DateTime? cursor, int limit, CancellationToken ct)
    {
        var query = _context.Posts
            .Include(p => p.Images.OrderBy(i => i.Order))
            .Include(p => p.PostHashtags).ThenInclude(ph => ph.Hashtag)
            .Where(p => p.PostHashtags.Any(ph => ph.Hashtag.Name == hashtag) && p.Status != PostStatus.Deleted);

        if (cursor.HasValue)
            query = query.Where(p => p.CreatedAt < cursor.Value);

        return await query
            .OrderByDescending(p => p.CreatedAt)
            .Take(limit)
            .ToListAsync(ct);
    }

    public async Task<List<Post>> GetByIdsAsync(List<Guid> ids, CancellationToken ct)
    {
        return await _context.Posts
            .Include(p => p.Images.OrderBy(i => i.Order))
            .Include(p => p.PostHashtags).ThenInclude(ph => ph.Hashtag)
            .Where(p => ids.Contains(p.Id))
            .ToListAsync(ct);
    }

    public async Task<List<Post>> GetCatalogPageAsync(DateTime? cursor, int limit, CancellationToken ct)
    {
        // Full-table sweep for catalog reconcile: every non-deleted post, oldest-skipping by
        // CreatedAt cursor like the other listings. No image include — the catalog projection
        // only needs the Recombee properties, so keep the scan light.
        var query = _context.Posts
            .Include(p => p.PostHashtags).ThenInclude(ph => ph.Hashtag)
            .Where(p => p.Status != PostStatus.Deleted);

        if (cursor.HasValue)
            query = query.Where(p => p.CreatedAt < cursor.Value);

        return await query
            .OrderByDescending(p => p.CreatedAt)
            .Take(limit)
            .ToListAsync(ct);
    }

    public async Task<List<Post>> SearchAsync(string query, DateTime? cursor, int limit, CancellationToken ct)
    {
        var searchPattern = $"%{LikeEscaping.EscapePattern(query)}%";
        var dbQuery = _context.Posts
            .Include(p => p.Images.OrderBy(i => i.Order))
            .Include(p => p.PostHashtags).ThenInclude(ph => ph.Hashtag)
            .Where(p => p.Status != PostStatus.Deleted &&
                (EF.Functions.ILike(p.Content ?? "", searchPattern) ||
                 p.PostHashtags.Any(ph => EF.Functions.ILike(ph.Hashtag.Name, searchPattern))));

        if (cursor.HasValue)
            dbQuery = dbQuery.Where(p => p.CreatedAt < cursor.Value);

        return await dbQuery
            .OrderByDescending(p => p.CreatedAt)
            .Take(limit)
            .ToListAsync(ct);
    }

    public async Task<Post> CreateAsync(Post post, CancellationToken ct)
    {
        _context.Posts.Add(post);
        await _context.SaveChangesAsync(ct);
        return post;
    }

    public async Task UpdateAsync(Post post, CancellationToken ct)
    {
        _context.Posts.Update(post);
        await _context.SaveChangesAsync(ct);
    }

    public async Task DeleteAsync(Post post, CancellationToken ct)
    {
        _context.Posts.Remove(post);
        await _context.SaveChangesAsync(ct);
    }

    public Task IncrementReactionsAsync(Guid postId, CancellationToken ct) =>
        _context.Posts.Where(p => p.Id == postId)
            .ExecuteUpdateAsync(s => s.SetProperty(p => p.ReactionsCount, p => p.ReactionsCount + 1), ct);

    public Task DecrementReactionsAsync(Guid postId, CancellationToken ct) =>
        _context.Posts.Where(p => p.Id == postId && p.ReactionsCount > 0)
            .ExecuteUpdateAsync(s => s.SetProperty(p => p.ReactionsCount, p => p.ReactionsCount - 1), ct);

    public Task IncrementCommentsAsync(Guid postId, CancellationToken ct) =>
        _context.Posts.Where(p => p.Id == postId)
            .ExecuteUpdateAsync(s => s.SetProperty(p => p.CommentsCount, p => p.CommentsCount + 1), ct);

    public Task DecrementCommentsAsync(Guid postId, CancellationToken ct) =>
        _context.Posts.Where(p => p.Id == postId && p.CommentsCount > 0)
            .ExecuteUpdateAsync(s => s.SetProperty(p => p.CommentsCount, p => p.CommentsCount - 1), ct);
}
