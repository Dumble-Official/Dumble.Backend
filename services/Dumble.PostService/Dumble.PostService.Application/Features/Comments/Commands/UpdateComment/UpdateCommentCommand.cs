using MediatR;
using Dumble.PostService.Contracts.Comments;

namespace Dumble.PostService.Application.Features.Comments.Commands.UpdateComment;

public record UpdateCommentCommand(Guid CommentId, string Content) : IRequest<CommentResponse>;
