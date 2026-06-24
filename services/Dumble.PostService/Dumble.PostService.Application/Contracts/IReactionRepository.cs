using Dumble.PostService.Domain.Entities;

namespace Dumble.PostService.Application.Contracts;

public interface IReactionRepository
{
    Task<Reaction?> GetByPostAndUserAsync(Guid postId, string userId, CancellationToken ct = default);

    /// <summary>
    /// Posts a user has reacted to, most-recently-reacted first, with the same
    /// cursor-by-timestamp pagination as the post listings. The cursor is the
    /// reaction's CreatedAt. Each tuple carries the reaction time so the caller
    /// can format the next cursor without re-querying. Deleted posts are excluded.
    /// </summary>
    Task<List<(Post Post, DateTime ReactedAt)>> GetReactedPostsByUserAsync(
        string userId, DateTime? cursor, int limit, CancellationToken ct = default);
    Task<List<Reaction>> GetByPostIdAsync(Guid postId, int offset, int limit, CancellationToken ct = default);
    Task<Dictionary<string, int>> GetCountsByPostIdAsync(Guid postId, CancellationToken ct = default);
    Task<Reaction> CreateAsync(Reaction reaction, CancellationToken ct = default);
    Task UpdateAsync(Reaction reaction, CancellationToken ct = default);
    Task DeleteAsync(Reaction reaction, CancellationToken ct = default);

    /// <summary>Delete every post reaction a user made — right-to-be-forgotten. Returns the count.</summary>
    Task<int> DeleteAllByUserAsync(string userId, CancellationToken ct = default);
}
