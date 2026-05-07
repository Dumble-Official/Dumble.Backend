using System.Net.Http.Headers;
using System.Net.Http.Json;
using Microsoft.AspNetCore.Http;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Contracts.Feed;

namespace Dumble.SocialService.Infrastructure.ExternalServices;

public class PostServiceClient : IPostServiceClient
{
    private readonly HttpClient _httpClient;
    private readonly IHttpContextAccessor _httpContextAccessor;

    public PostServiceClient(HttpClient httpClient, IHttpContextAccessor httpContextAccessor)
    {
        _httpClient = httpClient;
        _httpContextAccessor = httpContextAccessor;
    }

    public async Task<List<FeedPostResponse>> GetPostsByIdsAsync(List<string> postIds, CancellationToken ct = default)
    {
        // Forward the caller's bearer token so PostService can validate it
        // (downstream now requires auth on /api/posts/batch). Per-request
        // HttpRequestMessage avoids the DefaultRequestHeaders foot-gun on
        // any future lifetime change of the typed client.
        using var req = new HttpRequestMessage(HttpMethod.Post, "/api/posts/batch")
        {
            Content = JsonContent.Create(new { Ids = postIds })
        };
        var auth = _httpContextAccessor.HttpContext?.Request.Headers["Authorization"].ToString();
        if (!string.IsNullOrWhiteSpace(auth) && auth.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
        {
            req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", auth.Substring(7));
        }

        var response = await _httpClient.SendAsync(req, ct);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<List<FeedPostResponse>>(ct) ?? [];
    }
}
