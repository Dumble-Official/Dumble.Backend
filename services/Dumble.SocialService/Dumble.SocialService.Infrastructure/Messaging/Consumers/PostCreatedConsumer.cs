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
