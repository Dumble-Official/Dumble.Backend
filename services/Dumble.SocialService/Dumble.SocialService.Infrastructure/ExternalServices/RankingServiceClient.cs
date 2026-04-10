using System.Net.Http.Json;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Domain.Entities;

namespace Dumble.SocialService.Infrastructure.ExternalServices;

public class RankingServiceClient : IRankingServiceClient
{
    private readonly HttpClient _httpClient;

    public RankingServiceClient(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public async Task<List<string>> RankPostsAsync(
        string userId,
        List<string> followedUserIds,
        List<UserBehavior> recentBehavior,
        CancellationToken ct = default)
    {
        try
        {
            var request = new
            {
                UserId = userId,
                FollowedUserIds = followedUserIds,
                RecentBehavior = recentBehavior.Select(b => new
                {
                    b.PostId,
                    EventType = b.EventType.ToString(),
                    b.EventData,
                    b.CreatedAt
                })
            };

            var response = await _httpClient.PostAsJsonAsync("/rank", request, ct);
            response.EnsureSuccessStatusCode();
            return await response.Content.ReadFromJsonAsync<List<string>>(ct) ?? [];
        }
        catch
        {
            // Fallback: if AI ranking is unavailable, return empty (feed will be empty)
            return [];
        }
    }
}
