using MediatR;

namespace Dumble.PostService.Application.Features.Reactions.Commands.RemoveReaction;

public record RemoveReactionCommand(Guid PostId) : IRequest;
