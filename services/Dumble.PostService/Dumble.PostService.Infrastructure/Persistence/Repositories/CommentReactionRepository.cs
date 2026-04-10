using Microsoft.EntityFrameworkCore;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Domain.Entities;

namespace Dumble.PostService.Infrastructure.Persistence.Repositories;

public class CommentReactionRepository : ICommentReactionRepository
{
    private readonly PostDbContext _context;

    public CommentReactionRepository(PostDbContext context)
    {
        _context = context;
    }

    public async Task<CommentReaction?> GetByCommentAndUserAsync(Guid commentId, string userId, CancellationToken ct)
    {
        return await _context.CommentReactions
            .FirstOrDefaultAsync(cr => cr.CommentId == commentId && cr.UserId == userId, ct);
    }

    public async Task<CommentReaction> CreateAsync(CommentReaction reaction, CancellationToken ct)
    {
        _context.CommentReactions.Add(reaction);
        await _context.SaveChangesAsync(ct);
        return reaction;
    }

    public async Task UpdateAsync(CommentReaction reaction, CancellationToken ct)
    {
        _context.CommentReactions.Update(reaction);
        await _context.SaveChangesAsync(ct);
    }

    public async Task DeleteAsync(CommentReaction reaction, CancellationToken ct)
    {
        _context.CommentReactions.Remove(reaction);
        await _context.SaveChangesAsync(ct);
    }
}
