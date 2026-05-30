using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Application.Features.Catalog;
using Dumble.SharedKernel.Events.Posts;
using MassTransit;

namespace Dumble.RecommendationService.Infrastructure.Messaging.Consumers;

/// <summary>
/// Catalog sync: a new post becomes a Recombee item with its full profile, and is added to
/// the recent-posts index that backs the explore recency fallback / cold-start.
/// </summary>
public sealed class PostCreatedConsumer : IConsumer<PostCreatedEvent>
{
    private readonly IRecombeeClient _recombee;
    private readonly IRecentPostsStore _recentPosts;

    public PostCreatedConsumer(IRecombeeClient recombee, IRecentPostsStore recentPosts)
    {
        _recombee = recombee;
        _recentPosts = recentPosts;
    }

    public async Task Consume(ConsumeContext<PostCreatedEvent> context)
    {
        var e = context.Message;
        await _recombee.UpsertItemAsync(CatalogItemMapper.FromPostCreated(e), context.CancellationToken);
        await _recentPosts.AddAsync(e.PostId, e.CreatedAt, context.CancellationToken);
    }
}
