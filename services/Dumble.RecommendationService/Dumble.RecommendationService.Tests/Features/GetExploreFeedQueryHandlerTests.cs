using Dumble.RecommendationService.Application.Features.Feed.GetExploreFeed;
using Dumble.RecommendationService.Tests.TestDoubles;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace Dumble.RecommendationService.Tests.Features;

public class GetExploreFeedQueryHandlerTests
{
    private readonly FakeExploreFeedCache _cache = new();
    private readonly FakeRecombeeClient _recombee = new();
    private readonly FakeRecentPostsStore _recent = new();
    private readonly FakePostHydrator _hydrator = new();

    private GetExploreFeedQueryHandler Handler() =>
        new(_cache, _recombee, _recent, _hydrator, NullLogger<GetExploreFeedQueryHandler>.Instance);

    [Fact]
    public async Task Serves_from_cache_without_querying_recombee()
    {
        _cache.Seed("u1", "p1", "p2");

        var result = await Handler().Handle(new GetExploreFeedQuery("u1", null, 20), CancellationToken.None);

        Assert.Equal(new[] { "p1", "p2" }, result.Items.Select(i => i.Id));
        Assert.Equal(0, _recombee.RecommendCalls);
    }

    [Fact]
    public async Task On_cache_miss_uses_recombee_and_caches_the_snapshot()
    {
        _recombee.RecommendResult = new[] { "p1", "p2", "p3" };

        var result = await Handler().Handle(new GetExploreFeedQuery("u1", null, 20), CancellationToken.None);

        Assert.Equal(new[] { "p1", "p2", "p3" }, result.Items.Select(i => i.Id));
        Assert.Equal(1, _recombee.RecommendCalls);
        Assert.Equal(1, _cache.SetCalls);
    }

    [Fact]
    public async Task Falls_back_to_recency_when_recombee_returns_nothing()
    {
        _recombee.RecommendResult = Array.Empty<string>();
        _recent.Recent = new List<string> { "r1", "r2" };

        var result = await Handler().Handle(new GetExploreFeedQuery("u1", null, 20), CancellationToken.None);

        Assert.Equal(new[] { "r1", "r2" }, result.Items.Select(i => i.Id));
    }

    [Fact]
    public async Task Falls_back_to_recency_when_recombee_throws()
    {
        _recombee.ThrowOnRecommend = new InvalidOperationException("recombee down");
        _recent.Recent = new List<string> { "r1" };

        var result = await Handler().Handle(new GetExploreFeedQuery("u1", null, 20), CancellationToken.None);

        Assert.Equal(new[] { "r1" }, result.Items.Select(i => i.Id));
    }

    [Fact]
    public async Task Returns_empty_when_nothing_is_available()
    {
        _recombee.RecommendResult = Array.Empty<string>();
        _recent.Recent = new List<string>();

        var result = await Handler().Handle(new GetExploreFeedQuery("u1", null, 20), CancellationToken.None);

        Assert.Empty(result.Items);
        Assert.False(result.HasMore);
        Assert.Null(result.NextCursor);
    }

    [Fact]
    public async Task Paginates_from_the_cursor()
    {
        _cache.Seed("u1", "p1", "p2", "p3", "p4", "p5");

        var result = await Handler().Handle(new GetExploreFeedQuery("u1", "p2", 2), CancellationToken.None);

        Assert.Equal(new[] { "p3", "p4" }, result.Items.Select(i => i.Id));
        Assert.True(result.HasMore);
        Assert.Equal("p4", result.NextCursor);
    }

    [Fact]
    public async Task Drops_ids_the_hydrator_cannot_return_preserving_order()
    {
        _cache.Seed("u1", "p1", "p2", "p3");
        _hydrator.Missing.Add("p2");

        var result = await Handler().Handle(new GetExploreFeedQuery("u1", null, 20), CancellationToken.None);

        Assert.Equal(new[] { "p1", "p3" }, result.Items.Select(i => i.Id));
    }
}
