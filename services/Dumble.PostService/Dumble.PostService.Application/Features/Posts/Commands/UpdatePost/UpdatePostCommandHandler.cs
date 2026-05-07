using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Posts;
using Dumble.PostService.Domain.Entities;
using Dumble.SharedKernel.Contracts;

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
        var post = await _postRepository.GetByIdWithDetailsAsync(request.PostId, ct)
            ?? throw new KeyNotFoundException($"Post {request.PostId} not found");

        if (post.AuthorId != currentUser.Id)
            throw new UnauthorizedAccessException("You can only update your own posts");

        if (request.Content is not null)
            post.Content = request.Content;

        if (request.Hashtags is not null)
        {
            // Decrement old hashtag counts
            var oldHashtagIds = post.PostHashtags.Select(ph => ph.HashtagId).ToList();
            if (oldHashtagIds.Count > 0)
                await _hashtagRepository.DecrementUsageCountAsync(oldHashtagIds, ct);

            // Clear and re-add
            post.PostHashtags.Clear();
            var hashtagNames = request.Hashtags
                .Select(h => h.TrimStart('#').ToLowerInvariant().Trim())
                .Where(h => !string.IsNullOrEmpty(h))
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

        return new PostResponse(
            post.Id, post.AuthorId, post.AuthorDisplayName, post.AuthorProfileImage,
            post.AuthorType.ToString(), post.Content, post.GymId, post.Status.ToString(),
            post.ReactionsCount, post.CommentsCount,
            post.Images.Select(i => i.ImageUrl).ToList(),
            currentHashtags, post.CreatedAt, post.UpdatedAt
        );
    }
}
