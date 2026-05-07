using FastEndpoints;
using MediatR;
using Dumble.ChatService.Application.Features.Messages.Queries.GetMessages;
using Dumble.ChatService.Contracts.Common;
using Dumble.ChatService.Contracts.Messages;

namespace Dumble.ChatService.API.Endpoints.Messages;

public class GetMessagesEndpoint : EndpointWithoutRequest<CursorPagedResponse<MessageResponse>>
{
    private readonly IMediator _mediator;

    public GetMessagesEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/chat/conversations/{id}/messages");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var conversationId = Route<string>("id")!;
        var userId = User.FindFirst("userId")!.Value;
        var cursor = Query<string?>("cursor");
        var limit = Query<int?>("limit") ?? 20;

        var result = await _mediator.Send(new GetMessagesQuery(conversationId, userId, cursor, limit), ct);
        await SendAsync(result, cancellation: ct);
    }
}
