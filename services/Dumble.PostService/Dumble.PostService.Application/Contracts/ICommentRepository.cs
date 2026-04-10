using Dumble.PostService.Domain.Entities;

namespace Dumble.PostService.Application.Contracts;

public interface ICommentRepository
{
    Task<Comment?> GetByIdAsync(Guid id, CancellationToken ct = default);
    Task<List<Comment>> GetByPostIdAsync(Guid postId, DateTime? cursor, int limit, CancellationToken ct = default);
    Task<List<Comment>> GetRepliesAsync(Guid parentCommentId, DateTime? cursor, int limit, CancellationToken ct = default);
    Task<int> GetRepliesCountAsync(Guid parentCommentId, CancellationToken ct = default);
    Task<Comment> CreateAsync(Comment comment, CancellationToken ct = default);
    Task UpdateAsync(Comment comment, CancellationToken ct = default);
    Task DeleteAsync(Comment comment, CancellationToken ct = default);
}
