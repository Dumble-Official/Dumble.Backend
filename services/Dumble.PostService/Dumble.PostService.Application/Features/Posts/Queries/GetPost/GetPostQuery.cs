using MediatR;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.Application.Features.Posts.Queries.GetPost;

public record GetPostQuery(Guid PostId) : IRequest<PostResponse>;
