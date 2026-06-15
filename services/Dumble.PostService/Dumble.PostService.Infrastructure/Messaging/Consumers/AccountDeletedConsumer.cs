using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Domain.Enums;
using Dumble.SharedKernel.Events.Accounts;
using Dumble.SharedKernel.Events.Posts;
using MassTransit;
using Microsoft.Extensions.Logging;

namespace Dumble.PostService.Infrastructure.Messaging.Consumers;

/// <summary>
/// Right-to-be-forgotten cascade: when an account is deleted, soft-delete all of that user's posts
/// (announcing each so the recommendation service drops it from Recombee), soft-delete the comments
/// they wrote on other people's posts, and delete their post + comment reactions. Posts and comments
/// are only flagged Deleted — the rows and images are deliberately kept so the content can still
/// feed model training; this is not the user-initiated delete, which also purges images.
///
/// Denormalized ReactionsCount/CommentsCount on OTHER users' (still-live) posts may drift slightly
/// after this purge; that cosmetic skew is reconciled separately rather than walked per-post here.
/// </summary>
public sealed class AccountDeletedConsumer : IConsumer<AccountDeletedEvent>
{
    private const int BatchSize = 200;
    // Backstop against an unexpected non-shrinking query; far above any real per-user post count.
    private const int MaxBatches = 100_000;

    private readonly IPostRepository _postRepository;
    private readonly ICommentRepository _commentRepository;
    private readonly IReactionRepository _reactionRepository;
    private readonly ICommentReactionRepository _commentReactionRepository;
    private readonly IPublishEndpoint _publishEndpoint;
    private readonly ILogger<AccountDeletedConsumer> _logger;

    public AccountDeletedConsumer(
        IPostRepository postRepository,
        ICommentRepository commentRepository,
        IReactionRepository reactionRepository,
        ICommentReactionRepository commentReactionRepository,
        IPublishEndpoint publishEndpoint,
        ILogger<AccountDeletedConsumer> logger)
    {
        _postRepository = postRepository;
        _commentRepository = commentRepository;
        _reactionRepository = reactionRepository;
        _commentReactionRepository = commentReactionRepository;
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

        // Their comments on other people's posts (textual PII) — soft-delete, kept for training.
        var comments = await _commentRepository.SoftDeleteAllByAuthorAsync(authorId, ct);

        // Their reactions (a userId on each row = personal data) — hard-delete.
        var reactions = await _reactionRepository.DeleteAllByUserAsync(authorId, ct);
        var commentReactions = await _commentReactionRepository.DeleteAllByUserAsync(authorId, ct);

        _logger.LogInformation(
            "Forgot account {AuthorId}: soft-deleted {Posts} posts and {Comments} comments, removed {Reactions} post + {CommentReactions} comment reactions",
            authorId, deleted, comments, reactions, commentReactions);
    }
}
