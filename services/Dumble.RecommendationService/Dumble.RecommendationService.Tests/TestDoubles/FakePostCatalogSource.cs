using Dumble.RecommendationService.Application.Contracts;

namespace Dumble.RecommendationService.Tests.TestDoubles;

/// <summary>
/// Returns a pre-seeded sequence of catalog pages, advancing on each call. The cursor is the
/// next page index encoded as a string; a null next cursor on the last page ends the sweep.
/// </summary>
internal sealed class FakePostCatalogSource : IPostCatalogSource
{
    private readonly List<List<RecombeeItemUpsert>> _pages;

    public int Calls { get; private set; }

    public FakePostCatalogSource(params List<RecombeeItemUpsert>[] pages) => _pages = pages.ToList();

    public Task<PostCatalogPage> GetPageAsync(string? cursor, int limit, CancellationToken ct = default)
    {
        Calls++;
        var index = cursor is null ? 0 : int.Parse(cursor);
        var items = index < _pages.Count ? _pages[index] : new List<RecombeeItemUpsert>();
        var nextCursor = index + 1 < _pages.Count ? (index + 1).ToString() : null;
        return Task.FromResult(new PostCatalogPage(items, nextCursor));
    }
}
