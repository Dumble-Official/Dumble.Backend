using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Posts;
using Dumble.PostService.Domain.Enums;

namespace Dumble.PostService.Application.Features.Posts.Queries.GetPost;

public class GetPostQueryHandler : IRequestHandler<GetPostQuery, PostResponse>
{
    private readonly IPostRepository _postRepository;

    public GetPostQueryHandler(IPostRepository postRepository)
    {
        _postRepository = postRepository;
    }

    public async Task<PostResponse> Handle(GetPostQuery request, CancellationToken ct)
    {
        var post = await _postRepository.GetByIdWithDetailsAsync(request.PostId, ct)
            ?? throw new KeyNotFoundException($"Post {request.PostId} not found");

        if (post.Status == PostStatus.Deleted)
            throw new KeyNotFoundException($"Post {request.PostId} not found");

        var hashtags = post.PostHashtags
            .Select(ph => ph.Hashtag?.Name ?? string.Empty)
            .Where(n => !string.IsNullOrEmpty(n))
            .ToList();

        return new PostResponse(
            post.Id, post.AuthorId, post.AuthorDisplayName, post.AuthorProfileImage,
            post.AuthorType.ToString(), post.Content, post.GymId, post.Status.ToString(),
            post.ReactionsCount, post.CommentsCount,
            post.Images.Select(i => i.ImageUrl).ToList(),
            hashtags, post.CreatedAt, post.UpdatedAt
        );
    }
}
