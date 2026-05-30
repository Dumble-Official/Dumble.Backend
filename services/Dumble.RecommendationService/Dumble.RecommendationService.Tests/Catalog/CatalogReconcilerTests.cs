using Dumble.RecommendationService.Application.Catalog;
using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Tests.TestDoubles;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace Dumble.RecommendationService.Tests.Catalog;

public class CatalogReconcilerTests
{
    private static RecombeeItemUpsert Item(string id) => new(ItemId: id, Author: "u1");

    private static CatalogReconciler Build(FakePostCatalogSource source, FakeRecombeeClient client)
        => new(source, client, NullLogger<CatalogReconciler>.Instance);

    [Fact]
    public async Task Walks_every_page_and_upserts_all_items()
    {
        var source = new FakePostCatalogSource(
            new List<RecombeeItemUpsert> { Item("p1"), Item("p2") },
            new List<RecombeeItemUpsert> { Item("p3") });
        var client = new FakeRecombeeClient();

        var result = await Build(source, client).ReconcileAsync();

        Assert.Equal(2, result.Pages);
        Assert.Equal(3, result.Items);
        Assert.Equal(new[] { "p1", "p2", "p3" }, client.UpsertedItems.Select(i => i.ItemId));
        // One batch per non-empty page — not one call per item.
        Assert.Equal(2, client.UpsertBatchCalls);
    }

    [Fact]
    public async Task No_orphan_sweep_by_default()
    {
        var source = new FakePostCatalogSource(new List<RecombeeItemUpsert> { Item("p1") });
        var client = new FakeRecombeeClient { ItemIds = { "p1", "stale" } };

        var result = await Build(source, client).ReconcileAsync();

        // Sweep not requested → Recombee is never listed and nothing is deleted.
        Assert.Equal(0, result.OrphanCandidates);
        Assert.Empty(client.DeletedItems);
    }

    [Fact]
    public async Task Orphan_sweep_dry_run_reports_but_does_not_delete()
    {
        var source = new FakePostCatalogSource(new List<RecombeeItemUpsert> { Item("p1"), Item("p2") })
        {
            LiveIds = { "p1", "p2" } // PostService confirms "stale" is gone
        };
        var client = new FakeRecombeeClient { ItemIds = { "p1", "p2", "stale" } };

        var result = await Build(source, client).ReconcileAsync(sweepOrphans: true, dryRun: true);

        Assert.Equal(1, result.OrphanCandidates);
        Assert.Equal(0, result.OrphansDeleted);
        Assert.Empty(client.DeletedItems);
    }

    [Fact]
    public async Task Orphan_sweep_deletes_only_items_postservice_confirms_gone()
    {
        var source = new FakePostCatalogSource(new List<RecombeeItemUpsert> { Item("p1") })
        {
            // "skipped" is still live (a cursor skip), "stale" is genuinely gone.
            LiveIds = { "p1", "skipped" }
        };
        var client = new FakeRecombeeClient { ItemIds = { "p1", "skipped", "stale" } };

        var result = await Build(source, client).ReconcileAsync(sweepOrphans: true, dryRun: false);

        // Both "skipped" and "stale" are absent from the catalog page, so both are candidates...
        Assert.Equal(2, result.OrphanCandidates);
        // ...but only "stale" is confirmed gone by PostService, so only it is deleted.
        Assert.Equal(1, result.OrphansDeleted);
        Assert.Equal(new[] { "stale" }, client.DeletedItems);
    }

    [Fact]
    public async Task Empty_catalog_upserts_nothing()
    {
        var source = new FakePostCatalogSource(new List<RecombeeItemUpsert>());
        var client = new FakeRecombeeClient();

        var result = await Build(source, client).ReconcileAsync();

        Assert.Equal(1, result.Pages);
        Assert.Equal(0, result.Items);
        Assert.Empty(client.UpsertedItems);
        Assert.Equal(0, client.UpsertBatchCalls);
    }
}
