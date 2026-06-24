using MediatR;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.Application.Features.Posts.Queries.GetUserPostCount;

public record GetUserPostCountQuery(string UserId) : IRequest<PostCountResponse>;
