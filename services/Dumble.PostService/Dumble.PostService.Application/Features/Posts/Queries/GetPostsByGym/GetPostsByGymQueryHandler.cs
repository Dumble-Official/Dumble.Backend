using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Common;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.Application.Features.Posts.Queries.GetPostsByGym;

public class GetPostsByGymQueryHandler : IRequestHandler<GetPostsByGymQuery, CursorPagedResponse<PostResponse>>
{
    private readonly IPostRepository _postRepository;

    public GetPostsByGymQueryHandler(IPostRepository postRepository)
    {
        _postRepository = postRepository;
    }

    public async Task<CursorPagedResponse<PostResponse>> Handle(GetPostsByGymQuery request, CancellationToken ct)
    {
        DateTime? cursor = request.Cursor is not null ? DateTime.Parse(request.Cursor) : null;

        var posts = await _postRepository.GetByGymIdAsync(request.GymId, cursor, request.Limit + 1, ct);
        var hasMore = posts.Count > request.Limit;
        if (hasMore) posts = posts.Take(request.Limit).ToList();

        var items = posts.Select(post => new PostResponse(
            post.Id, post.AuthorId, post.AuthorDisplayName, post.AuthorProfileImage,
            post.AuthorType.ToString(), post.Content, post.GymId, post.Status.ToString(),
            post.ReactionsCount, post.CommentsCount,
            post.Images.Select(i => i.ImageUrl).ToList(),
            post.PostHashtags.Select(ph => ph.Hashtag?.Name ?? string.Empty).Where(n => !string.IsNullOrEmpty(n)).ToList(),
            post.CreatedAt, post.UpdatedAt
        )).ToList();

        var nextCursor = hasMore && items.Count > 0
            ? items.Last().CreatedAt.ToString("O")
            : null;

        return new CursorPagedResponse<PostResponse>(items, nextCursor, hasMore);
    }
}
