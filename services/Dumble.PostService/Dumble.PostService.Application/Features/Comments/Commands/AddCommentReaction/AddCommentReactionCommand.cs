using MediatR;
using Dumble.PostService.Contracts.Reactions;

namespace Dumble.PostService.Application.Features.Comments.Commands.AddCommentReaction;

public record AddCommentReactionCommand(Guid CommentId, string Type) : IRequest<ReactionResponse>;
