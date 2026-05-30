using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Domain.Enums;
using Dumble.SharedKernel.Events.Accounts;
using Dumble.SharedKernel.Events.Posts;
using MassTransit;
using Microsoft.Extensions.Logging;

namespace Dumble.PostService.Infrastructure.Messaging.Consumers;

/// <summary>
/// Right-to-be-forgotten cascade: when an account is deleted, soft-delete all of that user's posts
/// and announce each one so the recommendation service drops it from Recombee. Posts are only
/// flagged <see cref="PostStatus.Deleted"/> — the rows and their images are deliberately kept so
/// the content can still feed model training; this is not the user-initiated delete, which also
/// purges images.
/// </summary>
public sealed class AccountDeletedConsumer : IConsumer<AccountDeletedEvent>
{
    private const int BatchSize = 200;
    // Backstop against an unexpected non-shrinking query; far above any real per-user post count.
    private const int MaxBatches = 100_000;

    private readonly IPostRepository _postRepository;
    private readonly IPublishEndpoint _publishEndpoint;
    private readonly ILogger<AccountDeletedConsumer> _logger;

    public AccountDeletedConsumer(
        IPostRepository postRepository,
        IPublishEndpoint publishEndpoint,
        ILogger<AccountDeletedConsumer> logger)
    {
        _postRepository = postRepository;
        _publishEndpoint = publishEndpoint;
        _logger = logger;
    }

    public async Task Consume(ConsumeContext<AccountDeletedEvent> context)
    {
        var authorId = context.Message.UserId;
        if (string.IsNullOrWhiteSpace(authorId))
        {
            _logger.LogWarning("Account-deleted event carried no userId; no posts to soft-delete");
            return;
        }

        var ct = context.CancellationToken;
        var deleted = 0;

        // Each pass fetches the next page of still-active posts. Soft-deleting them removes them
        // from that filter, so a fresh cursorless fetch keeps making progress until none remain.
        for (var batch = 0; batch < MaxBatches; batch++)
        {
            var posts = await _postRepository.GetByAuthorIdAsync(authorId, cursor: null, limit: BatchSize, ct);
            if (posts.Count == 0)
                break;

            foreach (var post in posts)
            {
                post.Status = PostStatus.Deleted;
                post.UpdatedAt = DateTime.UtcNow;
                await _postRepository.UpdateAsync(post, ct);

                await _publishEndpoint.Publish(new PostDeletedEvent(post.Id.ToString(), post.AuthorId), ct);
                deleted++;
            }
        }

        _logger.LogInformation("Soft-deleted {Count} posts for deleted account {AuthorId}", deleted, authorId);
    }
}
