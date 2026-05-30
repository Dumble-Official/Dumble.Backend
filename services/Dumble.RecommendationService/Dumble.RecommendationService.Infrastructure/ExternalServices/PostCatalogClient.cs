using System.Net.Http.Headers;
using System.Net.Http.Json;
using Dumble.RecommendationService.Application.Contracts;

namespace Dumble.RecommendationService.Infrastructure.ExternalServices;

/// <summary>
/// Pages PostService's catalog endpoint, presenting a minted service token (no end-user request
/// is in flight during a reconcile). Maps each lean catalog row to a Recombee upsert so the
/// reconciler stays free of any transport detail.
/// </summary>
public sealed class PostCatalogClient : IPostCatalogSource
{
    private readonly HttpClient _httpClient;
    private readonly IServiceTokenProvider _tokenProvider;

    public PostCatalogClient(HttpClient httpClient, IServiceTokenProvider tokenProvider)
    {
        _httpClient = httpClient;
        _tokenProvider = tokenProvider;
    }

    public async Task<PostCatalogPage> GetPageAsync(string? cursor, int limit, CancellationToken ct = default)
    {
        var url = $"/api/posts/catalog?limit={limit}";
        if (!string.IsNullOrEmpty(cursor))
            url += $"&cursor={Uri.EscapeDataString(cursor)}";

        using var request = new HttpRequestMessage(HttpMethod.Get, url);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", _tokenProvider.CreateToken());

        var response = await _httpClient.SendAsync(request, ct);
        response.EnsureSuccessStatusCode();

        var page = await response.Content.ReadFromJsonAsync<CatalogPageDto>(ct);
        if (page?.Items is null || page.Items.Count == 0)
            return new PostCatalogPage(Array.Empty<RecombeeItemUpsert>(), page?.NextCursor);

        var items = page.Items
            .Select(i => new RecombeeItemUpsert(
                ItemId: i.Id.ToString(),
                Author: i.AuthorId,
                AuthorType: i.AuthorType,
                GymId: i.GymId,
                Hashtags: i.Hashtags ?? new List<string>(),
                CreatedAt: i.CreatedAt))
            .ToList();

        return new PostCatalogPage(items, page.NextCursor);
    }

    public async Task<IReadOnlySet<string>> GetExistingIdsAsync(IReadOnlyList<string> ids, CancellationToken ct = default)
    {
        if (ids.Count == 0)
            return new HashSet<string>();

        // /api/posts/batch returns only live posts (it filters out soft-deleted), so the ids it
        // echoes back are exactly the ones that still exist.
        using var request = new HttpRequestMessage(HttpMethod.Post, "/api/posts/batch")
        {
            Content = JsonContent.Create(new { Ids = ids })
        };
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", _tokenProvider.CreateToken());

        var response = await _httpClient.SendAsync(request, ct);
        response.EnsureSuccessStatusCode();

        var posts = await response.Content.ReadFromJsonAsync<List<BatchPostDto>>(ct) ?? new List<BatchPostDto>();
        return posts.Select(p => p.Id.ToString()).ToHashSet();
    }

    // Structural match to PostService's CursorPagedResponse<PostCatalogItem> — kept private so the
    // rec service does not take a project reference on PostService's contracts (same as hydration).
    private sealed record CatalogPageDto(List<CatalogItemDto> Items, string? NextCursor, bool HasMore);

    // We only need the id back from the batch response; other fields are ignored on deserialize.
    private sealed record BatchPostDto(Guid Id);

    private sealed record CatalogItemDto(
        Guid Id,
        string AuthorId,
        string AuthorType,
        string? GymId,
        List<string>? Hashtags,
        DateTimeOffset CreatedAt);
}
