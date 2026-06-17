using Dumble.SocialService.Contracts.Common;
using Dumble.SocialService.Contracts.Feed;

namespace Dumble.SocialService.Application.Contracts;

/// <summary>
/// Fetches the Recombee-ranked home feed from the recommendation service. SocialService no longer
/// ranks feeds itself — it owns the follow graph; the recommendation service owns all ranking.
/// </summary>
public interface IHomeFeedClient
{
    Task<CursorPagedResponse<FeedPostResponse>> GetHomeFeedAsync(string? cursor, int limit, CancellationToken ct = default);
}
