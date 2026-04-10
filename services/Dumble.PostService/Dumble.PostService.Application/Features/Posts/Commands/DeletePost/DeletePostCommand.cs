using MediatR;

namespace Dumble.PostService.Application.Features.Posts.Commands.DeletePost;

public record DeletePostCommand(Guid PostId) : IRequest;
