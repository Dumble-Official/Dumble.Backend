using MediatR;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.Application.Features.Posts.Queries.BatchGetPosts;

public record BatchGetPostsQuery(List<Guid> Ids) : IRequest<List<PostResponse>>;
