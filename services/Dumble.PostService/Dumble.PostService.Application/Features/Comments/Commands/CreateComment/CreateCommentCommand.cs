using MediatR;
using Dumble.PostService.Contracts.Comments;

namespace Dumble.PostService.Application.Features.Comments.Commands.CreateComment;

public record CreateCommentCommand(Guid PostId, string Content, Guid? ParentCommentId) : IRequest<CommentResponse>;
