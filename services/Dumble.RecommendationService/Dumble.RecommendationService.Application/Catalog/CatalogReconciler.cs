using Dumble.RecommendationService.Application.Contracts;
using Microsoft.Extensions.Logging;

namespace Dumble.RecommendationService.Application.Catalog;

public sealed record CatalogReconcileResult(int Pages, int Items);

/// <summary>
/// Catalog reconcile (D17): walk PostService's catalog page by page and upsert each page into
/// Recombee. Because upserts are idempotent (cascadeCreate), re-sending an item that is already
/// in sync is harmless — so this heals any drift left by a missed PostCreated/PostUpdated event
/// without needing to diff. Deleted posts are absent from the catalog, so they are simply not
/// re-created; a hard orphan sweep (deleting items no longer present) is a separate follow-up.
/// </summary>
public sealed class CatalogReconciler
{
    private readonly IPostCatalogSource _source;
    private readonly IRecombeeClient _recombee;
    private readonly ILogger<CatalogReconciler> _logger;

    public const int PageSize = 200;
    // Safety backstop so a bad cursor can never spin forever; far above any real catalog size.
    public const int MaxPages = 100_000;

    public CatalogReconciler(IPostCatalogSource source, IRecombeeClient recombee, ILogger<CatalogReconciler> logger)
    {
        _source = source;
        _recombee = recombee;
        _logger = logger;
    }

    public async Task<CatalogReconcileResult> ReconcileAsync(CancellationToken ct = default)
    {
        string? cursor = null;
        var pages = 0;
        var items = 0;

        do
        {
            var page = await _source.GetPageAsync(cursor, PageSize, ct);
            if (page.Items.Count > 0)
            {
                await _recombee.UpsertItemsAsync(page.Items, ct);
                items += page.Items.Count;
            }

            pages++;
            cursor = page.NextCursor;
        }
        while (cursor is not null && pages < MaxPages && !ct.IsCancellationRequested);

        if (pages >= MaxPages)
            _logger.LogWarning("Catalog reconcile hit the {MaxPages}-page safety cap; sweep may be incomplete", MaxPages);

        _logger.LogInformation("Catalog reconcile complete: upserted {Items} items across {Pages} pages", items, pages);
        return new CatalogReconcileResult(pages, items);
    }
}
