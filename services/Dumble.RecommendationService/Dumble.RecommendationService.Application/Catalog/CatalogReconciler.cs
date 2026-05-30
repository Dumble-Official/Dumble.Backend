using Dumble.RecommendationService.Application.Contracts;
using Microsoft.Extensions.Logging;

namespace Dumble.RecommendationService.Application.Catalog;

public sealed record CatalogReconcileResult(int Pages, int Items, int OrphanCandidates = 0, int OrphansDeleted = 0);

/// <summary>
/// Catalog reconcile (D17): walk PostService's catalog page by page and upsert each page into
/// Recombee. Because upserts are idempotent (cascadeCreate), re-sending an item that is already
/// in sync is harmless — so this heals any drift left by a missed PostCreated/PostUpdated event
/// without needing to diff.
///
/// An optional orphan sweep then removes Recombee items that outlived their source post (e.g. a
/// dropped delete event). Each candidate is confirmed absent against PostService before deletion,
/// so a post the catalog cursor merely skipped is never deleted. The sweep can run in report-only
/// (dry-run) mode so its decisions can be observed before real deletes are turned on.
/// </summary>
public sealed class CatalogReconciler
{
    private readonly IPostCatalogSource _source;
    private readonly IRecombeeClient _recombee;
    private readonly ILogger<CatalogReconciler> _logger;

    public const int PageSize = 200;
    // /api/posts/batch caps a request at 100 ids, so confirm orphan candidates in chunks of 100.
    public const int ConfirmBatchSize = 100;
    // Safety backstop so a bad cursor can never spin forever; far above any real catalog size.
    public const int MaxPages = 100_000;

    public CatalogReconciler(IPostCatalogSource source, IRecombeeClient recombee, ILogger<CatalogReconciler> logger)
    {
        _source = source;
        _recombee = recombee;
        _logger = logger;
    }

    public async Task<CatalogReconcileResult> ReconcileAsync(bool sweepOrphans = false, bool dryRun = true, CancellationToken ct = default)
    {
        string? cursor = null;
        var pages = 0;
        var items = 0;
        var live = new HashSet<string>();

        do
        {
            var page = await _source.GetPageAsync(cursor, PageSize, ct);
            if (page.Items.Count > 0)
            {
                await _recombee.UpsertItemsAsync(page.Items, ct);
                items += page.Items.Count;
                foreach (var item in page.Items)
                    live.Add(item.ItemId);
            }

            pages++;
            cursor = page.NextCursor;
        }
        while (cursor is not null && pages < MaxPages && !ct.IsCancellationRequested);

        if (pages >= MaxPages)
            _logger.LogWarning("Catalog reconcile hit the {MaxPages}-page safety cap; sweep may be incomplete", MaxPages);

        _logger.LogInformation("Catalog reconcile complete: upserted {Items} items across {Pages} pages", items, pages);

        var (candidates, deleted) = sweepOrphans
            ? await SweepOrphansAsync(live, dryRun, ct)
            : (0, 0);

        return new CatalogReconcileResult(pages, items, candidates, deleted);
    }

    private async Task<(int Candidates, int Deleted)> SweepOrphansAsync(HashSet<string> live, bool dryRun, CancellationToken ct)
    {
        var allItems = await _recombee.ListItemIdsAsync(ct);
        var candidates = allItems.Where(id => !live.Contains(id)).ToList();
        if (candidates.Count == 0)
            return (0, 0);

        // Confirm against the source of truth: anything /api/posts/batch does not echo back is
        // genuinely gone. This guards against deleting a live post the catalog cursor skipped.
        var orphans = new List<string>();
        foreach (var chunk in candidates.Chunk(ConfirmBatchSize))
        {
            var stillLive = await _source.GetExistingIdsAsync(chunk, ct);
            orphans.AddRange(chunk.Where(id => !stillLive.Contains(id)));
        }

        if (orphans.Count == 0)
            return (candidates.Count, 0);

        if (dryRun)
        {
            _logger.LogWarning(
                "Orphan sweep (dry-run): {Count} Recombee items are absent from PostService and would be deleted; sample: {Sample}",
                orphans.Count, string.Join(",", orphans.Take(20)));
            return (candidates.Count, 0);
        }

        foreach (var id in orphans)
            await _recombee.DeleteItemAsync(id, ct);

        _logger.LogInformation("Orphan sweep: deleted {Count} Recombee items no longer present in PostService", orphans.Count);
        return (candidates.Count, orphans.Count);
    }
}
