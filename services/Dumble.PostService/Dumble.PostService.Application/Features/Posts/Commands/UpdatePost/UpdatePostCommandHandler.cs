using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Posts;
using Dumble.PostService.Domain.Entities;
using Dumble.PostService.Domain.Enums;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Enums;

namespace Dumble.PostService.Application.Features.Posts.Commands.UpdatePost;

public class UpdatePostCommandHandler : IRequestHandler<UpdatePostCommand, PostResponse>
{
    private readonly IPostRepository _postRepository;
    private readonly IHashtagRepository _hashtagRepository;
    private readonly ILoggedInUserService _userService;

    public UpdatePostCommandHandler(
        IPostRepository postRepository,
        IHashtagRepository hashtagRepository,
        ILoggedInUserService userService)
    {
        _postRepository = postRepository;
        _hashtagRepository = hashtagRepository;
        _userService = userService;
    }

    public async Task<PostResponse> Handle(UpdatePostCommand request, CancellationToken ct)
    {
        var currentUser = _userService.GetCurrentUser();
        var post = await _postRepository.GetByIdWithDetailsAsync(request.PostId, ct);

        if (post is null || post.Status == PostStatus.Deleted)
            throw new KeyNotFoundException($"Post {request.PostId} not found");

        // NOTE: race window — between this read and UpdateAsync below, a
        // concurrent SoftDelete can flip Status to Deleted and our save would
        // overwrite. Tracked as a follow-up: needs an EF rowversion concurrency
        // token (schema change) OR a WHERE clause guard in UpdateAsync via
        // ExecuteUpdateAsync. Out of scope for this PR.

        var canModerate = currentUser.IsInAnyRole(UserType.Admin, UserType.Moderator);
        if (post.AuthorId != currentUser.Id && !canModerate)
            throw new UnauthorizedAccessException("You can only update your own posts");

        if (request.Content is not null)
            post.Content = request.Content;

        if (request.Hashtags is not null)
        {
            var oldHashtagIds = post.PostHashtags.Select(ph => ph.HashtagId).ToList();
            if (oldHashtagIds.Count > 0)
                await _hashtagRepository.DecrementUsageCountAsync(oldHashtagIds, ct);

            post.PostHashtags.Clear();
            var hashtagNames = request.Hashtags
                .Where(h => !string.IsNullOrWhiteSpace(h))
                .Select(h => h.Trim().TrimStart('#').ToLowerInvariant())
                .Where(h => h.Length > 0)
                .Distinct()
                .ToList();

            if (hashtagNames.Count > 0)
            {
                var hashtags = await _hashtagRepository.GetOrCreateManyAsync(hashtagNames, ct);
                foreach (var hashtag in hashtags)
                {
                    post.PostHashtags.Add(new PostHashtag { PostId = post.Id, HashtagId = hashtag.Id });
                }
                await _hashtagRepository.IncrementUsageCountAsync(hashtags.Select(h => h.Id).ToList(), ct);
            }
        }

        post.UpdatedAt = DateTime.UtcNow;
        await _postRepository.UpdateAsync(post, ct);

        var currentHashtags = post.PostHashtags
            .Select(ph => ph.Hashtag?.Name ?? string.Empty)
            .Where(n => !string.IsNullOrEmpty(n))
            .ToList();

        // PostUpdatedEvent publish removed: no consumer exists yet (neither
        // SocialService nor NotificationService binds to post.updated.*). A
        // fire-and-forget publish to nothing is wasted I/O + a misleading
        // audit trail. Re-add together with the consumer + outbox in a
        // follow-up PR.

        return new PostResponse(
            post.Id, post.AuthorId, post.AuthorDisplayName, post.AuthorProfileImage,
            post.AuthorType.ToString(), post.Content, post.GymId, post.Status.ToString(),
            post.ReactionsCount, post.CommentsCount,
            post.Images.Select(i => i.ImageUrl).ToList(),
            currentHashtags, post.CreatedAt, post.UpdatedAt
        );
    }
}
