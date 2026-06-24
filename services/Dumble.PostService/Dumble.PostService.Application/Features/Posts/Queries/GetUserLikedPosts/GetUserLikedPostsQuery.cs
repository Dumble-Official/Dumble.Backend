using MediatR;
using Dumble.PostService.Contracts.Common;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.Application.Features.Posts.Queries.GetUserLikedPosts;

public record GetUserLikedPostsQuery(string UserId, string? Cursor, int Limit = 20)
    : IRequest<CursorPagedResponse<PostResponse>>;
