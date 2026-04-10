using Dumble.SocialService.Domain.Entities;

namespace Dumble.SocialService.Application.Contracts;

public interface IUserBehaviorRepository
{
    Task CreateAsync(UserBehavior behavior, CancellationToken ct = default);
    Task CreateBatchAsync(List<UserBehavior> behaviors, CancellationToken ct = default);
    Task<List<UserBehavior>> GetRecentAsync(string userId, int limit, CancellationToken ct = default);
}
