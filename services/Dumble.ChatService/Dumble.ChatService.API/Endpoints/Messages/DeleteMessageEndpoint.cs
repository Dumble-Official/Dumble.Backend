using FastEndpoints;
using MediatR;
using Dumble.ChatService.Application.Features.Messages.Commands.DeleteMessage;

namespace Dumble.ChatService.API.Endpoints.Messages;

public class DeleteMessageEndpoint : EndpointWithoutRequest
{
    private readonly IMediator _mediator;

    public DeleteMessageEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Delete("/api/chat/messages/{messageId}");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var messageId = Route<string>("messageId")!;
        await _mediator.Send(new DeleteMessageCommand(messageId), ct);
        await SendNoContentAsync(ct);
    }
}
