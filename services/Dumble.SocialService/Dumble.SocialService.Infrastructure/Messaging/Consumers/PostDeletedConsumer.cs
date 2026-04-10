using MassTransit;
using Dumble.SharedKernel.Events.Posts;
using Dumble.SocialService.Application.Contracts;

namespace Dumble.SocialService.Infrastructure.Messaging.Consumers;

public class PostDeletedConsumer : IConsumer<PostDeletedEvent>
{
    private readonly IFeedCacheService _feedCache;
    private readonly IFollowRepository _followRepository;

    public PostDeletedConsumer(IFeedCacheService feedCache, IFollowRepository followRepository)
    {
        _feedCache = feedCache;
        _followRepository = followRepository;
    }

    public async Task Consume(ConsumeContext<PostDeletedEvent> context)
    {
        // Invalidate feed caches when a post is deleted
        var evt = context.Message;
        var allFollowers = new List<string>();
        DateTime? cursor = null;

        while (true)
        {
            var batch = await _followRepository.GetFollowersAsync(evt.AuthorId, cursor, 500, context.CancellationToken);
            if (batch.Count == 0) break;
            allFollowers.AddRange(batch.Select(f => f.FollowerId));
            if (batch.Count < 500) break;
            cursor = batch.Last().CreatedAt;
        }

        if (allFollowers.Count > 0)
            await _feedCache.InvalidateFeedsForFollowersAsync(evt.AuthorId, allFollowers, context.CancellationToken);
    }
}
