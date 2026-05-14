using MediatR;
using MassTransit;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Posts;
using Dumble.PostService.Domain.Entities;
using Dumble.PostService.Domain.Enums;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Enums;
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
        var currentUser = _userService.GetCurrentUser();

        var post = new Post
        {
            Id = Guid.NewGuid(),
            AuthorId = currentUser.Id,
            AuthorDisplayName = currentUser.DisplayName,
            AuthorProfileImage = currentUser.ProfileImage,
            AuthorType = currentUser.UserType,
            Content = request.Content,
            GymId = request.GymId,
            Status = PostStatus.Active,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        if (request.Images is { Count: > 0 })
        {
            for (var i = 0; i < request.Images.Count; i++)
            {
                var image = request.Images[i];
                // Dispose the upload stream as soon as we're done with it.
                // Without this a client disconnecting mid-upload leaves the
                // ASP.NET buffer pinned until GC; under load that exhausts
                // file descriptors and the request pool.
                await using var content = image.Content;
                var (url, publicId) = await _fileService.UploadAsync(content, image.FileName, image.ContentType, ct);

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

        var hashtagNames = new List<string>();
        if (request.Hashtags is { Count: > 0 })
        {
            hashtagNames = request.Hashtags
                .Where(h => !string.IsNullOrWhiteSpace(h))
                .Select(h => h.Trim().TrimStart('#').ToLowerInvariant())
                .Where(h => h.Length > 0)
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
            post.AuthorType,
            post.GymId,
            hashtagNames,
            new DateTimeOffset(post.CreatedAt, TimeSpan.Zero)
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
