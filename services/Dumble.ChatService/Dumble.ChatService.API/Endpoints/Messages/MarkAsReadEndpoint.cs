using FastEndpoints;
using MediatR;
using Dumble.ChatService.Application.Features.Messages.Commands.MarkAsRead;

namespace Dumble.ChatService.API.Endpoints.Messages;

public class MarkAsReadEndpoint : EndpointWithoutRequest
{
    private readonly IMediator _mediator;

    public MarkAsReadEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Put("/api/chat/conversations/{id}/read");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var conversationId = Route<string>("id")!;
        var userId = User.FindFirst("userId")!.Value;
        var messageId = Query<string>("messageId")!;

        await _mediator.Send(new MarkAsReadCommand(conversationId, userId, messageId), ct);
        await SendNoContentAsync(ct);
    }
}
