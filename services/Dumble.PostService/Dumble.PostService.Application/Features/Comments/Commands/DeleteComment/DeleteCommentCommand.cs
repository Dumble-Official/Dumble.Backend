using MediatR;

namespace Dumble.PostService.Application.Features.Comments.Commands.DeleteComment;

public record DeleteCommentCommand(Guid CommentId) : IRequest;
