using Dumble.RecommendationService.Application.Features.Feed.GetHomeFeed;
using Dumble.RecommendationService.Tests.TestDoubles;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace Dumble.RecommendationService.Tests.Features;

public class GetHomeFeedQueryHandlerTests
{
    private readonly FakeFollowProjection _follows = new();
    private readonly FakeRecombeeClient _recombee = new();
    private readonly FakePostHydrator _hydrator = new();

    private GetHomeFeedQueryHandler Handler() =>
        new(_follows, _recombee, _hydrator, NullLogger<GetHomeFeedQueryHandler>.Instance);

    [Fact]
    public async Task Returns_empty_when_user_follows_no_one()
    {
        var result = await Handler().Handle(new GetHomeFeedQuery("u1", null, 20), CancellationToken.None);

        Assert.Empty(result.Items);
        Assert.Null(_recombee.LastFollowedAuthorIds); // never queried Recombee
    }

    [Fact]
    public async Task Ranks_followee_posts_via_recombee_filtered_to_the_followee_set()
    {
        _follows.Seed("u1", "a1", "a2");
        _recombee.RecommendFollowedResult = new[] { "p1", "p2", "p3" };

        var result = await Handler().Handle(new GetHomeFeedQuery("u1", null, 20), CancellationToken.None);

        Assert.Equal(new[] { "p1", "p2", "p3" }, result.Items.Select(i => i.Id));
        Assert.Equal(new[] { "a1", "a2" }, _recombee.LastFollowedAuthorIds!.OrderBy(x => x));
    }

    [Fact]
    public async Task Degrades_to_empty_when_recombee_is_unavailable()
    {
        _follows.Seed("u1", "a1");
        _recombee.ThrowOnRecommendFollowed = new InvalidOperationException("recombee down");

        var result = await Handler().Handle(new GetHomeFeedQuery("u1", null, 20), CancellationToken.None);

        Assert.Empty(result.Items);
    }

    [Fact]
    public async Task Drops_ids_postservice_cannot_hydrate()
    {
        _follows.Seed("u1", "a1");
        _recombee.RecommendFollowedResult = new[] { "p1", "gone", "p2" };
        _hydrator.Missing.Add("gone");

        var result = await Handler().Handle(new GetHomeFeedQuery("u1", null, 20), CancellationToken.None);

        Assert.Equal(new[] { "p1", "p2" }, result.Items.Select(i => i.Id));
    }
}
