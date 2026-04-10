using Dumble.PostService.Domain.Entities;

namespace Dumble.PostService.Application.Contracts;

public interface IReactionRepository
{
    Task<Reaction?> GetByPostAndUserAsync(Guid postId, string userId, CancellationToken ct = default);
    Task<List<Reaction>> GetByPostIdAsync(Guid postId, int offset, int limit, CancellationToken ct = default);
    Task<Dictionary<string, int>> GetCountsByPostIdAsync(Guid postId, CancellationToken ct = default);
    Task<Reaction> CreateAsync(Reaction reaction, CancellationToken ct = default);
    Task UpdateAsync(Reaction reaction, CancellationToken ct = default);
    Task DeleteAsync(Reaction reaction, CancellationToken ct = default);
}
