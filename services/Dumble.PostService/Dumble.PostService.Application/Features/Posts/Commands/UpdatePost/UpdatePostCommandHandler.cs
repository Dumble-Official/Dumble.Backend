using MediatR;
using MassTransit;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Posts;
using Dumble.PostService.Domain.Entities;
using Dumble.PostService.Domain.Enums;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Enums;
using Dumble.SharedKernel.Events.Posts;

namespace Dumble.PostService.Application.Features.Posts.Commands.UpdatePost;

public class UpdatePostCommandHandler : IRequestHandler<UpdatePostCommand, PostResponse>
{
    private readonly IPostRepository _postRepository;
    private readonly IHashtagRepository _hashtagRepository;
    private readonly ILoggedInUserService _userService;
    private readonly IPublishEndpoint _publishEndpoint;

    public UpdatePostCommandHandler(
        IPostRepository postRepository,
        IHashtagRepository hashtagRepository,
        ILoggedInUserService userService,
        IPublishEndpoint publishEndpoint)
    {
        _postRepository = postRepository;
        _hashtagRepository = hashtagRepository;
        _userService = userService;
        _publishEndpoint = publishEndpoint;
    }

    public async Task<PostResponse> Handle(UpdatePostCommand request, CancellationToken ct)
    {
        var currentUser = _userService.GetCurrentUser();
        var post = await _postRepository.GetByIdWithDetailsAsync(request.PostId, ct);

        if (post is null || post.Status == PostStatus.Deleted)
            throw new KeyNotFoundException($"Post {request.PostId} not found");

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

        await _publishEndpoint.Publish(new PostUpdatedEvent(
            post.Id.ToString(),
            post.AuthorId,
            currentHashtags,
            new DateTimeOffset(post.UpdatedAt, TimeSpan.Zero)
        ), ct);

        return new PostResponse(
            post.Id, post.AuthorId, post.AuthorDisplayName, post.AuthorProfileImage,
            post.AuthorType.ToString(), post.Content, post.GymId, post.Status.ToString(),
            post.ReactionsCount, post.CommentsCount,
            post.Images.Select(i => i.ImageUrl).ToList(),
            currentHashtags, post.CreatedAt, post.UpdatedAt
        );
    }
}
