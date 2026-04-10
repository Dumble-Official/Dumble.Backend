using Dumble.PostService.Domain.Entities;

namespace Dumble.PostService.Application.Contracts;

public interface IHashtagRepository
{
    Task<Hashtag?> GetByNameAsync(string name, CancellationToken ct = default);
    Task<List<Hashtag>> GetOrCreateManyAsync(List<string> names, CancellationToken ct = default);
    Task<List<Hashtag>> GetTrendingAsync(int limit, CancellationToken ct = default);
    Task<List<Hashtag>> SearchAsync(string query, int limit, CancellationToken ct = default);
    Task IncrementUsageCountAsync(List<Guid> hashtagIds, CancellationToken ct = default);
    Task DecrementUsageCountAsync(List<Guid> hashtagIds, CancellationToken ct = default);
}
