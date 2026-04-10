using MassTransit;
using Dumble.SharedKernel.Events.Posts;
using Dumble.SocialService.Application.Contracts;

namespace Dumble.SocialService.Infrastructure.Messaging.Consumers;

public class PostCreatedConsumer : IConsumer<PostCreatedEvent>
{
    private readonly IFollowRepository _followRepository;
    private readonly IFeedCacheService _feedCache;

    public PostCreatedConsumer(IFollowRepository followRepository, IFeedCacheService feedCache)
    {
        _followRepository = followRepository;
        _feedCache = feedCache;
    }

    public async Task Consume(ConsumeContext<PostCreatedEvent> context)
    {
        var evt = context.Message;
        var followerIds = await _followRepository.GetFolloweeIdsAsync(evt.AuthorId, context.CancellationToken);

        // Invalidate feed caches for all followers of the post author
        // Note: GetFolloweeIdsAsync returns who the author follows, we need who follows the author
        // Actually we need followers OF the author, so we need a different query
        // Let's use the followRepository to get the followers list
        // followerIds here are people the author follows. We actually need people who follow the author.
        // Fix: get followers of the author
        var followers = await GetFollowerIdsOfAuthor(evt.AuthorId, context.CancellationToken);
        if (followers.Count > 0)
            await _feedCache.InvalidateFeedsForFollowersAsync(evt.AuthorId, followers, context.CancellationToken);
    }

    private async Task<List<string>> GetFollowerIdsOfAuthor(string authorId, CancellationToken ct)
    {
        // Get all followers (people who follow the author) - paginate through all
        var allFollowers = new List<string>();
        DateTime? cursor = null;
        const int batchSize = 500;

        while (true)
        {
            var batch = await _followRepository.GetFollowersAsync(authorId, cursor, batchSize, ct);
            if (batch.Count == 0) break;

            allFollowers.AddRange(batch.Select(f => f.FollowerId));
            if (batch.Count < batchSize) break;
            cursor = batch.Last().CreatedAt;
        }

        return allFollowers;
    }
}
