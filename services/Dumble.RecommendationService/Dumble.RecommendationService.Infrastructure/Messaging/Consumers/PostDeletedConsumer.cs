using Dumble.RecommendationService.Application.Contracts;
using Dumble.SharedKernel.Events.Posts;
using MassTransit;

namespace Dumble.RecommendationService.Infrastructure.Messaging.Consumers;

/// <summary>
/// Catalog sync: a deleted post is hard-deleted from Recombee (D11) and dropped from the
/// recent-posts index. PostService keeps the soft-deleted row for future model training.
/// </summary>
public sealed class PostDeletedConsumer : IConsumer<PostDeletedEvent>
{
    private readonly IRecombeeClient _recombee;
    private readonly IRecentPostsStore _recentPosts;

    public PostDeletedConsumer(IRecombeeClient recombee, IRecentPostsStore recentPosts)
    {
        _recombee = recombee;
        _recentPosts = recentPosts;
    }

    public async Task Consume(ConsumeContext<PostDeletedEvent> context)
    {
        var postId = context.Message.PostId;
        await _recombee.DeleteItemAsync(postId, context.CancellationToken);
        await _recentPosts.RemoveAsync(postId, context.CancellationToken);
    }
}
