using Dumble.RecommendationService.Application.Contracts;
using Dumble.SharedKernel.Events.Posts;
using MassTransit;

namespace Dumble.RecommendationService.Infrastructure.Messaging.Consumers;

/// <summary>
/// Moderation: a flagged post is removed from Recombee and the recency index so it stops being
/// recommended. Unlike PostDeleted this is reversible — unflagging makes the post Active again and
/// the catalog reconcile re-adds it. The item delete is idempotent, so a re-flag is harmless.
/// </summary>
public sealed class PostFlaggedConsumer : IConsumer<PostFlaggedEvent>
{
    private readonly IRecombeeClient _recombee;
    private readonly IRecentPostsStore _recentPosts;

    public PostFlaggedConsumer(IRecombeeClient recombee, IRecentPostsStore recentPosts)
    {
        _recombee = recombee;
        _recentPosts = recentPosts;
    }

    public async Task Consume(ConsumeContext<PostFlaggedEvent> context)
    {
        var postId = context.Message.PostId;
        await _recombee.DeleteItemAsync(postId, context.CancellationToken);
        await _recentPosts.RemoveAsync(postId, context.CancellationToken);
    }
}
