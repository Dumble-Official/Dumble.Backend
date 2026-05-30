using System.Net.Http.Headers;
using System.Net.Http.Json;
using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Contracts.Feed;
using Microsoft.AspNetCore.Http;

namespace Dumble.RecommendationService.Infrastructure.ExternalServices;

/// <summary>
/// Hydrates ranked post ids from PostService's batch endpoint, forwarding the caller's bearer
/// token so PostService authorizes the request (same approach SocialService uses). PostService
/// omits soft-deleted posts, which is the guarantee that deleted content is never rendered.
/// </summary>
public sealed class PostServiceClient : IPostHydrator
{
    private readonly HttpClient _httpClient;
    private readonly IHttpContextAccessor _httpContextAccessor;

    public PostServiceClient(HttpClient httpClient, IHttpContextAccessor httpContextAccessor)
    {
        _httpClient = httpClient;
        _httpContextAccessor = httpContextAccessor;
    }

    public async Task<IReadOnlyList<FeedPostResponse>> HydrateAsync(IReadOnlyList<string> postIds, CancellationToken ct = default)
    {
        if (postIds.Count == 0)
            return Array.Empty<FeedPostResponse>();

        using var request = new HttpRequestMessage(HttpMethod.Post, "/api/posts/batch")
        {
            Content = JsonContent.Create(new { Ids = postIds })
        };

        var auth = _httpContextAccessor.HttpContext?.Request.Headers["Authorization"].ToString();
        if (!string.IsNullOrWhiteSpace(auth) && auth.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", auth["Bearer ".Length..]);

        var response = await _httpClient.SendAsync(request, ct);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<List<FeedPostResponse>>(ct) ?? new List<FeedPostResponse>();
    }
}
