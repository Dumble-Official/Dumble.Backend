using System.Net.Http.Json;
using Microsoft.Extensions.Logging;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Domain.Entities;

namespace Dumble.SocialService.Infrastructure.ExternalServices;

public class RankingServiceClient : IRankingServiceClient
{
    private readonly HttpClient _httpClient;
    private readonly ILogger<RankingServiceClient> _logger;
    private readonly bool _isConfigured;

    public RankingServiceClient(HttpClient httpClient, ILogger<RankingServiceClient> logger)
    {
        _httpClient = httpClient;
        _logger = logger;
        _isConfigured = httpClient.BaseAddress != null
            && !httpClient.BaseAddress.Host.Contains("ranking-not-configured", StringComparison.OrdinalIgnoreCase);
        if (!_isConfigured)
        {
            _logger.LogWarning("RankingApi is not configured (Services:RankingApi env var missing). Feed ranking is disabled.");
        }
    }

    public async Task<List<string>> RankPostsAsync(
        string userId,
        List<string> followedUserIds,
        List<UserBehavior> recentBehavior,
        CancellationToken ct = default)
    {
        if (!_isConfigured) return [];

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

        try
        {
            var response = await _httpClient.PostAsJsonAsync("/rank", request, ct);
            response.EnsureSuccessStatusCode();
            return await response.Content.ReadFromJsonAsync<List<string>>(ct) ?? [];
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            // Honour caller cancellation rather than swallowing it.
            throw;
        }
        catch (HttpRequestException ex)
        {
            _logger.LogWarning(ex, "Ranking service unreachable for user {UserId}", userId);
            return [];
        }
        catch (TaskCanceledException ex)
        {
            _logger.LogWarning(ex, "Ranking service timed out for user {UserId}", userId);
            return [];
        }
    }
}
