using MediatR;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.Application.Features.Posts.Commands.UpdatePost;

public record UpdatePostCommand(
    Guid PostId,
    string? Content,
    List<string>? Hashtags
) : IRequest<PostResponse>;
