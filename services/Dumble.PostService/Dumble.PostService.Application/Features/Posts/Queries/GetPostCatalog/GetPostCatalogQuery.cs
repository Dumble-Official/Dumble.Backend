using MediatR;
using Dumble.PostService.Contracts.Common;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.Application.Features.Posts.Queries.GetPostCatalog;

public record GetPostCatalogQuery(string? Cursor, int Limit) : IRequest<CursorPagedResponse<PostCatalogItem>>;
