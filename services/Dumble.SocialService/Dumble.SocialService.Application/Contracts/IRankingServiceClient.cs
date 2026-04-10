using Dumble.SocialService.Domain.Entities;

namespace Dumble.SocialService.Application.Contracts;

public interface IRankingServiceClient
{
    Task<List<string>> RankPostsAsync(string userId, List<string> followedUserIds, List<UserBehavior> recentBehavior, CancellationToken ct = default);
}
