using Microsoft.EntityFrameworkCore;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Domain.Entities;
using Dumble.PostService.Domain.Enums;

namespace Dumble.PostService.Infrastructure.Persistence.Repositories;

public class CommentRepository : ICommentRepository
{
    private readonly PostDbContext _context;

    public CommentRepository(PostDbContext context)
    {
        _context = context;
    }

    public async Task<Comment?> GetByIdAsync(Guid id, CancellationToken ct)
    {
        return await _context.Comments
            .FirstOrDefaultAsync(c => c.Id == id && c.Status != CommentStatus.Deleted, ct);
    }

    public async Task<List<Comment>> GetByPostIdAsync(Guid postId, DateTime? cursor, int limit, CancellationToken ct)
    {
        var query = _context.Comments
            .Where(c => c.PostId == postId && c.ParentCommentId == null && c.Status != CommentStatus.Deleted);

        if (cursor.HasValue)
            query = query.Where(c => c.CreatedAt < cursor.Value);

        return await query
            .OrderByDescending(c => c.CreatedAt)
            .Take(limit)
            .ToListAsync(ct);
    }

    public async Task<List<Comment>> GetRepliesAsync(Guid parentCommentId, DateTime? cursor, int limit, CancellationToken ct)
    {
        var query = _context.Comments
            .Where(c => c.ParentCommentId == parentCommentId && c.Status != CommentStatus.Deleted);

        if (cursor.HasValue)
            query = query.Where(c => c.CreatedAt < cursor.Value);

        return await query
            .OrderByDescending(c => c.CreatedAt)
            .Take(limit)
            .ToListAsync(ct);
    }

    public async Task<int> GetRepliesCountAsync(Guid parentCommentId, CancellationToken ct)
    {
        return await _context.Comments
            .CountAsync(c => c.ParentCommentId == parentCommentId && c.Status != CommentStatus.Deleted, ct);
    }

    public async Task<Comment> CreateAsync(Comment comment, CancellationToken ct)
    {
        _context.Comments.Add(comment);
        await _context.SaveChangesAsync(ct);
        return comment;
    }

    public async Task UpdateAsync(Comment comment, CancellationToken ct)
    {
        _context.Comments.Update(comment);
        await _context.SaveChangesAsync(ct);
    }

    public async Task DeleteAsync(Comment comment, CancellationToken ct)
    {
        _context.Comments.Remove(comment);
        await _context.SaveChangesAsync(ct);
    }
}
