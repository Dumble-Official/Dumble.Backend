using Dumble.PostService.Domain.Entities;

namespace Dumble.PostService.Application.Contracts;

public interface ICommentReactionRepository
{
    Task<CommentReaction?> GetByCommentAndUserAsync(Guid commentId, string userId, CancellationToken ct = default);
    Task<CommentReaction> CreateAsync(CommentReaction reaction, CancellationToken ct = default);
    Task UpdateAsync(CommentReaction reaction, CancellationToken ct = default);
    Task DeleteAsync(CommentReaction reaction, CancellationToken ct = default);

    /// <summary>Delete every comment reaction a user made — right-to-be-forgotten. Returns the count.</summary>
    Task<int> DeleteAllByUserAsync(string userId, CancellationToken ct = default);
}
