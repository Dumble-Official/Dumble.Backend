using MediatR;
using Dumble.PostService.Contracts.Common;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.Application.Features.Posts.Queries.GetPostsByHashtag;

public record GetPostsByHashtagQuery(string Tag, string? Cursor, int Limit = 20)
    : IRequest<CursorPagedResponse<PostResponse>>;
