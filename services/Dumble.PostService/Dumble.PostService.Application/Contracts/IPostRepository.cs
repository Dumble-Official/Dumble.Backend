using Dumble.PostService.Domain.Entities;

namespace Dumble.PostService.Application.Contracts;

public interface IPostRepository
{
    Task<Post?> GetByIdAsync(Guid id, CancellationToken ct = default);
    Task<Post?> GetByIdWithDetailsAsync(Guid id, CancellationToken ct = default);
    Task<List<Post>> GetByAuthorIdAsync(string authorId, DateTime? cursor, int limit, CancellationToken ct = default);
    Task<int> CountByAuthorIdAsync(string authorId, CancellationToken ct = default);
    Task<List<Post>> GetLikedByUserAsync(string userId, DateTime? cursor, int limit, CancellationToken ct = default);
    Task<List<Post>> GetByGymIdAsync(string gymId, DateTime? cursor, int limit, CancellationToken ct = default);
    Task<List<Post>> GetByHashtagAsync(string hashtag, DateTime? cursor, int limit, CancellationToken ct = default);
    Task<List<Post>> GetByIdsAsync(List<Guid> ids, CancellationToken ct = default);
    Task<List<Post>> GetCatalogPageAsync(DateTime? cursor, int limit, CancellationToken ct = default);
    Task<List<Post>> SearchAsync(string query, DateTime? cursor, int limit, CancellationToken ct = default);
    Task<Post> CreateAsync(Post post, CancellationToken ct = default);
    Task UpdateAsync(Post post, CancellationToken ct = default);
    Task DeleteAsync(Post post, CancellationToken ct = default);

    Task IncrementReactionsAsync(Guid postId, CancellationToken ct = default);
    Task DecrementReactionsAsync(Guid postId, CancellationToken ct = default);
    Task IncrementCommentsAsync(Guid postId, CancellationToken ct = default);
    Task DecrementCommentsAsync(Guid postId, CancellationToken ct = default);
}
