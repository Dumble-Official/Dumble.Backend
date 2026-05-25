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
        // Null-safe claim lookup. A JWT minted without the userId claim
        // would otherwise NPE → 500. The throw falls through to the
        // service-wide ExceptionMapping, which maps
        // UnauthorizedAccessException to 403 — same status as every other
        // auth-layer rejection in this service. If the team later decides
        // missing-claim cases should be 401 specifically, that's a single
        // change in ExceptionMapping.cs, not a per-endpoint override.
        var userId = User.FindFirst("userId")?.Value
            ?? throw new UnauthorizedAccessException("Token is missing the userId claim");
        await _mediator.Send(new EditMessageCommand(messageId, userId, req.Content), ct);
        await SendNoContentAsync(ct);
    }
}
