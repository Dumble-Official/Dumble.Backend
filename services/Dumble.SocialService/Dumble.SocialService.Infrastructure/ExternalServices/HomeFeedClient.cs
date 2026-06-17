using System.Net.Http.Headers;
using System.Net.Http.Json;
using Microsoft.AspNetCore.Http;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Contracts.Common;
using Dumble.SocialService.Contracts.Feed;

namespace Dumble.SocialService.Infrastructure.ExternalServices;

/// <summary>
/// Proxies the home feed to the recommendation service's GET /api/feed/home (Recombee-ranked),
/// forwarding the caller's bearer token so the recommendation service ranks for the right user.
/// </summary>
public class HomeFeedClient : IHomeFeedClient
{
    private readonly HttpClient _httpClient;
    private readonly IHttpContextAccessor _httpContextAccessor;

    public HomeFeedClient(HttpClient httpClient, IHttpContextAccessor httpContextAccessor)
    {
        _httpClient = httpClient;
        _httpContextAccessor = httpContextAccessor;
    }

    public async Task<CursorPagedResponse<FeedPostResponse>> GetHomeFeedAsync(
        string? cursor, int limit, CancellationToken ct = default)
    {
        var url = $"/api/feed/home?limit={limit}";
        if (!string.IsNullOrEmpty(cursor))
            url += $"&cursor={Uri.EscapeDataString(cursor)}";

        using var req = new HttpRequestMessage(HttpMethod.Get, url);
        var auth = _httpContextAccessor.HttpContext?.Request.Headers["Authorization"].ToString();
        if (!string.IsNullOrWhiteSpace(auth) && auth.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
            req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", auth.Substring(7));

        var response = await _httpClient.SendAsync(req, ct);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<CursorPagedResponse<FeedPostResponse>>(ct)
               ?? new CursorPagedResponse<FeedPostResponse>([], null, false);
    }
}
