using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Posts;
using Dumble.PostService.Domain.Enums;

namespace Dumble.PostService.Application.Features.Posts.Queries.BatchGetPosts;

public class BatchGetPostsQueryHandler : IRequestHandler<BatchGetPostsQuery, List<PostResponse>>
{
    private readonly IPostRepository _postRepository;

    public BatchGetPostsQueryHandler(IPostRepository postRepository)
    {
        _postRepository = postRepository;
    }

    public async Task<List<PostResponse>> Handle(BatchGetPostsQuery request, CancellationToken ct)
    {
        var posts = await _postRepository.GetByIdsAsync(request.Ids, ct);

        return posts
            .Where(p => p.Status != PostStatus.Deleted)
            .Select(post => new PostResponse(
                post.Id, post.AuthorId, post.AuthorDisplayName, post.AuthorProfileImage,
                post.AuthorType.ToString(), post.Content, post.GymId, post.Status.ToString(),
                post.ReactionsCount, post.CommentsCount,
                post.Images.Select(i => i.ImageUrl).ToList(),
                post.PostHashtags.Select(ph => ph.Hashtag?.Name ?? string.Empty).Where(n => !string.IsNullOrEmpty(n)).ToList(),
                post.CreatedAt, post.UpdatedAt
            )).ToList();
    }
}
