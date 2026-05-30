using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Application.Features.Common;
using Dumble.PostService.Contracts.Common;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.Application.Features.Posts.Queries.GetPostCatalog;

public class GetPostCatalogQueryHandler : IRequestHandler<GetPostCatalogQuery, CursorPagedResponse<PostCatalogItem>>
{
    private readonly IPostRepository _postRepository;

    public GetPostCatalogQueryHandler(IPostRepository postRepository)
    {
        _postRepository = postRepository;
    }

    public async Task<CursorPagedResponse<PostCatalogItem>> Handle(GetPostCatalogQuery request, CancellationToken ct)
    {
        var cursor = CursorParsing.ParseUtcCursor(request.Cursor);

        var posts = await _postRepository.GetCatalogPageAsync(cursor, request.Limit + 1, ct);
        var hasMore = posts.Count > request.Limit;
        if (hasMore) posts = posts.Take(request.Limit).ToList();

        var items = posts.Select(post => new PostCatalogItem(
            post.Id,
            post.AuthorId,
            post.AuthorType.ToString(),
            post.GymId,
            post.PostHashtags.Select(ph => ph.Hashtag?.Name ?? string.Empty).Where(n => !string.IsNullOrEmpty(n)).ToList(),
            post.CreatedAt
        )).ToList();

        var nextCursor = hasMore && items.Count > 0
            ? CursorParsing.FormatCursor(items.Last().CreatedAt)
            : null;

        return new CursorPagedResponse<PostCatalogItem>(items, nextCursor, hasMore);
    }
}
