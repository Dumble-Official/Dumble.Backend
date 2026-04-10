using MediatR;
using MassTransit;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Posts;
using Dumble.PostService.Domain.Entities;
using Dumble.PostService.Domain.Enums;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Events.Posts;

namespace Dumble.PostService.Application.Features.Posts.Commands.CreatePost;

public class CreatePostCommandHandler : IRequestHandler<CreatePostCommand, PostResponse>
{
    private readonly IPostRepository _postRepository;
    private readonly IHashtagRepository _hashtagRepository;
    private readonly IFileService _fileService;
    private readonly ILoggedInUserService _userService;
    private readonly IPublishEndpoint _publishEndpoint;

    public CreatePostCommandHandler(
        IPostRepository postRepository,
        IHashtagRepository hashtagRepository,
        IFileService fileService,
        ILoggedInUserService userService,
        IPublishEndpoint publishEndpoint)
    {
        _postRepository = postRepository;
        _hashtagRepository = hashtagRepository;
        _fileService = fileService;
        _userService = userService;
        _publishEndpoint = publishEndpoint;
    }

    public async Task<PostResponse> Handle(CreatePostCommand request, CancellationToken ct)
    {
        var currentUser = await _userService.GetCurrentUserAsync(ct);

        var post = new Post
        {
            Id = Guid.NewGuid(),
            AuthorId = currentUser.Id,
            AuthorDisplayName = currentUser.DisplayName,
            AuthorProfileImage = currentUser.ProfileImage,
            AuthorType = Enum.Parse<AuthorType>(currentUser.UserType.ToString()),
            Content = request.Content,
            GymId = request.GymId,
            Status = PostStatus.Active,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        // Upload images
        if (request.Images is { Count: > 0 })
        {
            for (int i = 0; i < request.Images.Count; i++)
            {
                var file = request.Images[i];
                await using var stream = file.OpenReadStream();
                var (url, publicId) = await _fileService.UploadAsync(stream, file.FileName, file.ContentType, ct);

                post.Images.Add(new PostImage
                {
                    Id = Guid.NewGuid(),
                    PostId = post.Id,
                    ImageUrl = url,
                    PublicId = publicId,
                    Order = i
                });
            }
        }

        // Process hashtags
        var hashtagNames = new List<string>();
        if (request.Hashtags is { Count: > 0 })
        {
            hashtagNames = request.Hashtags
                .Select(h => h.TrimStart('#').ToLowerInvariant().Trim())
                .Where(h => !string.IsNullOrEmpty(h))
                .Distinct()
                .ToList();

            var hashtags = await _hashtagRepository.GetOrCreateManyAsync(hashtagNames, ct);
            foreach (var hashtag in hashtags)
            {
                post.PostHashtags.Add(new PostHashtag
                {
                    PostId = post.Id,
                    HashtagId = hashtag.Id
                });
            }
            await _hashtagRepository.IncrementUsageCountAsync(hashtags.Select(h => h.Id).ToList(), ct);
        }

        await _postRepository.CreateAsync(post, ct);

        // Publish event
        await _publishEndpoint.Publish(new PostCreatedEvent(
            post.Id.ToString(),
            post.AuthorId,
            post.AuthorType.ToString(),
            post.GymId,
            hashtagNames,
            post.CreatedAt
        ), ct);

        return new PostResponse(
            post.Id,
            post.AuthorId,
            post.AuthorDisplayName,
            post.AuthorProfileImage,
            post.AuthorType.ToString(),
            post.Content,
            post.GymId,
            post.Status.ToString(),
            post.ReactionsCount,
            post.CommentsCount,
            post.Images.Select(img => img.ImageUrl).ToList(),
            hashtagNames,
            post.CreatedAt,
            post.UpdatedAt
        );
    }
}
