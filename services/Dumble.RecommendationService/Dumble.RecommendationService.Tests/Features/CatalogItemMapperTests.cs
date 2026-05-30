using Dumble.RecommendationService.Application.Features.Catalog;
using Dumble.SharedKernel.Enums;
using Dumble.SharedKernel.Events.Posts;
using Xunit;

namespace Dumble.RecommendationService.Tests.Features;

public class CatalogItemMapperTests
{
    private static readonly DateTimeOffset CreatedAt = new(2026, 5, 20, 10, 0, 0, TimeSpan.Zero);

    [Fact]
    public void PostCreated_maps_to_a_full_item_profile()
    {
        var e = new PostCreatedEvent("p1", "author1", UserType.Trainer, "gym1",
            new[] { "legday", "squats" }, CreatedAt);

        var item = CatalogItemMapper.FromPostCreated(e);

        Assert.Equal("p1", item.ItemId);
        Assert.Equal("author1", item.Author);
        Assert.Equal(UserType.Trainer.ToString(), item.AuthorType);
        Assert.Equal("gym1", item.GymId);
        Assert.Equal(new[] { "legday", "squats" }, item.Hashtags);
        Assert.Equal(CreatedAt, item.CreatedAt);
    }

    [Fact]
    public void PostUpdated_maps_to_a_partial_item_touching_only_known_fields()
    {
        var e = new PostUpdatedEvent("p1", "author1", new[] { "rest" }, CreatedAt);

        var item = CatalogItemMapper.FromPostUpdated(e);

        Assert.Equal("p1", item.ItemId);
        Assert.Equal("author1", item.Author);
        Assert.Equal(new[] { "rest" }, item.Hashtags);
        // Update knows nothing about these, so they must stay untouched (null = not sent).
        Assert.Null(item.AuthorType);
        Assert.Null(item.GymId);
        Assert.Null(item.CreatedAt);
    }
}
