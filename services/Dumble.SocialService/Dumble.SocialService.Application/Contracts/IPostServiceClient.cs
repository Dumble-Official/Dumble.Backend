using Dumble.SocialService.Contracts.Feed;

namespace Dumble.SocialService.Application.Contracts;

public interface IPostServiceClient
{
    Task<List<FeedPostResponse>> GetPostsByIdsAsync(List<string> postIds, CancellationToken ct = default);
}
