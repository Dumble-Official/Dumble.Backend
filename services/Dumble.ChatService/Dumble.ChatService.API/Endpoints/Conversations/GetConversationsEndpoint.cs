using FastEndpoints;
using MediatR;
using Dumble.ChatService.Application.Features.Conversations.Queries.GetConversations;
using Dumble.ChatService.Contracts.Common;
using Dumble.ChatService.Contracts.Conversations;

namespace Dumble.ChatService.API.Endpoints.Conversations;

public class GetConversationsEndpoint : EndpointWithoutRequest<CursorPagedResponse<ConversationResponse>>
{
    private readonly IMediator _mediator;

    public GetConversationsEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/chat/conversations");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var userId = User.FindFirst("userId")!.Value;
        var cursor = Query<string?>("cursor", isRequired: false);
        var limit = Math.Clamp(Query<int?>("limit", isRequired: false) ?? 20, 1, 100);

        var result = await _mediator.Send(new GetConversationsQuery(userId, cursor, limit), ct);
        await SendAsync(result, cancellation: ct);
    }
}
