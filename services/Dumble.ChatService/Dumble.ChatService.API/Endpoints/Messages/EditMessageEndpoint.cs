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
        // Claims("userId")), but a JWT minted without it would otherwise
        // throw NullReferenceException and surface as 500. Issue 401 here
        // — the global ExceptionMapping maps UnauthorizedAccessException
        // to 403 (used for "authenticated but lacks permission"), which is
        // semantically wrong for "token doesn't identify a user".
        var userId = User.FindFirst("userId")?.Value;
        if (userId is null)
        {
            await SendUnauthorizedAsync(ct);
            return;
        }
        await _mediator.Send(new EditMessageCommand(messageId, userId, req.Content), ct);
        await SendNoContentAsync(ct);
    }
}
