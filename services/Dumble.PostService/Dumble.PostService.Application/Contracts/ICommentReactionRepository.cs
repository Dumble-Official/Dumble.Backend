using Dumble.PostService.Domain.Entities;

namespace Dumble.PostService.Application.Contracts;

public interface ICommentReactionRepository
{
    Task<CommentReaction?> GetByCommentAndUserAsync(Guid commentId, string userId, CancellationToken ct = default);
    Task<CommentReaction> CreateAsync(CommentReaction reaction, CancellationToken ct = default);
    Task UpdateAsync(CommentReaction reaction, CancellationToken ct = default);
    Task DeleteAsync(CommentReaction reaction, CancellationToken ct = default);
}
