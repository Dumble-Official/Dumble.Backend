using MediatR;
using Dumble.PostService.Contracts.Reactions;

namespace Dumble.PostService.Application.Features.Reactions.Commands.AddReaction;

public record AddReactionCommand(Guid PostId, string Type) : IRequest<ReactionResponse>;
