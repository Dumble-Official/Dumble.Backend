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
