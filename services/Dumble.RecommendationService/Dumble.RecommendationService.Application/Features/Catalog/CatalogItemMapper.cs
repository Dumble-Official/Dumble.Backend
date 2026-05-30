using Dumble.RecommendationService.Application.Contracts;
using Dumble.SharedKernel.Events.Posts;

namespace Dumble.RecommendationService.Application.Features.Catalog;

/// <summary>
/// Maps post catalog events to Recombee item upserts. Create carries the full profile;
/// update carries only what <see cref="PostUpdatedEvent"/> knows (author + hashtags), leaving
/// the other properties untouched. Pure, so it unit-tests without a broker or the SDK.
/// </summary>
public static class CatalogItemMapper
{
    public static RecombeeItemUpsert FromPostCreated(PostCreatedEvent e) => new(
        ItemId: e.PostId,
        Author: e.AuthorId,
        AuthorType: e.AuthorType.ToString(),
        GymId: e.GymId,
        Hashtags: e.Hashtags,
        CreatedAt: e.CreatedAt);

    public static RecombeeItemUpsert FromPostUpdated(PostUpdatedEvent e) => new(
        ItemId: e.PostId,
        Author: e.AuthorId,
        Hashtags: e.Hashtags);
}
