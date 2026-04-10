using System.Net.Http.Json;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Contracts.Feed;

namespace Dumble.SocialService.Infrastructure.ExternalServices;

public class PostServiceClient : IPostServiceClient
{
    private readonly HttpClient _httpClient;

    public PostServiceClient(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public async Task<List<FeedPostResponse>> GetPostsByIdsAsync(List<string> postIds, CancellationToken ct = default)
    {
        var response = await _httpClient.PostAsJsonAsync("/api/posts/batch", new { Ids = postIds }, ct);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<List<FeedPostResponse>>(ct) ?? [];
    }
}
