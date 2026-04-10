using MediatR;

namespace Dumble.PostService.Application.Features.Comments.Commands.RemoveCommentReaction;

public record RemoveCommentReactionCommand(Guid CommentId) : IRequest;
