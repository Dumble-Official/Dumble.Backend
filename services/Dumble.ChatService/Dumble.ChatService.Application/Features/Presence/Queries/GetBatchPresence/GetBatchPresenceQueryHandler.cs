using Dumble.ChatService.Application.Contracts;
using Dumble.ChatService.Contracts.Presence;
using MediatR;

namespace Dumble.ChatService.Application.Features.Presence.Queries.GetBatchPresence;

public class GetBatchPresenceQueryHandler(
    IPresenceService presenceService
) : IRequestHandler<GetBatchPresenceQuery, BatchPresenceResponse>
{
    public async Task<BatchPresenceResponse> Handle(
        GetBatchPresenceQuery request, CancellationToken cancellationToken)
    {
        var statuses = await presenceService.GetBatchOnlineStatusAsync(
            request.UserIds, cancellationToken);

        return new BatchPresenceResponse(statuses);
    }
}
