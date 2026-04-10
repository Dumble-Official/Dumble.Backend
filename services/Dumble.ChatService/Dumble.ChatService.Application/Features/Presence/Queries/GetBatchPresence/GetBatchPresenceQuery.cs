using Dumble.ChatService.Contracts.Presence;
using MediatR;

namespace Dumble.ChatService.Application.Features.Presence.Queries.GetBatchPresence;

public sealed record GetBatchPresenceQuery(
    List<string> UserIds
) : IRequest<BatchPresenceResponse>;
