using FastEndpoints;
using MediatR;
using Dumble.ChatService.Application.Features.Messages.Commands.EditMessage;
using Dumble.ChatService.Contracts.Messages;

namespace Dumble.ChatService.API.Endpoints.Messages;

public class EditMessageEndpoint : Endpoint<EditMessageRequest>
{
    private readonly IMediator _mediator;

    public EditMessageEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Put("/api/chat/messages/{messageId}");
        Claims("userId");
    }

    public override async Task HandleAsync(EditMessageRequest req, CancellationToken ct)
    {
        var messageId = Route<string>("messageId")!;
        // The userId claim is mandatory for this endpoint (registered via
        // Claims("userId")), but a JWT minted without it would otherwise throw
        // a raw NullReferenceException and surface as 500 instead of 401.
        var userId = User.FindFirst("userId")?.Value
            ?? throw new UnauthorizedAccessException("Token is missing the userId claim");
        await _mediator.Send(new EditMessageCommand(messageId, userId, req.Content), ct);
        await SendNoContentAsync(ct);
    }
}
