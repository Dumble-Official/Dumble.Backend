using Dumble.SocialService.Domain.Entities;

namespace Dumble.SocialService.Application.Contracts;

public interface IFollowRepository
{
    Task<Follow?> GetAsync(string followerId, string followeeId, CancellationToken ct = default);
    Task CreateAsync(Follow follow, CancellationToken ct = default);
    Task<bool> DeleteAsync(string followerId, string followeeId, CancellationToken ct = default);
    Task<List<Follow>> GetFollowersAsync(string userId, DateTime? cursor, int limit, CancellationToken ct = default);
    Task<List<Follow>> GetFollowingAsync(string userId, DateTime? cursor, int limit, CancellationToken ct = default);
    Task<int> GetFollowersCountAsync(string userId, CancellationToken ct = default);
    Task<int> GetFollowingCountAsync(string userId, CancellationToken ct = default);
    Task<Dictionary<string, bool>> GetFollowStatusBatchAsync(string followerId, List<string> followeeIds, CancellationToken ct = default);
    Task<List<string>> GetFolloweeIdsAsync(string userId, CancellationToken ct = default);
}
