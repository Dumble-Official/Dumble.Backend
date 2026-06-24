using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Application.Features.Common;
using Dumble.PostService.Contracts.Common;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.Application.Features.Posts.Queries.GetLikedPosts;

public class GetLikedPostsQueryHandler : IRequestHandler<GetLikedPostsQuery, CursorPagedResponse<PostResponse>>
{
    private readonly IPostRepository _postRepository;

    public GetLikedPostsQueryHandler(IPostRepository postRepository)
    {
        _postRepository = postRepository;
    }

    public async Task<CursorPagedResponse<PostResponse>> Handle(GetLikedPostsQuery request, CancellationToken ct)
    {
        var cursor = CursorParsing.ParseUtcCursor(request.Cursor);

        var posts = await _postRepository.GetLikedByUserAsync(request.UserId, cursor, request.Limit + 1, ct);
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
            ? CursorParsing.FormatCursor(items.Last().CreatedAt)
            : null;

        return new CursorPagedResponse<PostResponse>(items, nextCursor, hasMore);
    }
}
