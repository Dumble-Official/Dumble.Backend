using FastEndpoints;
using MediatR;
using Dumble.ChatService.Application.Features.Conversations.Commands.AddParticipants;
using Dumble.ChatService.Contracts.Conversations;

namespace Dumble.ChatService.API.Endpoints.Conversations;

public class AddParticipantsEndpoint : Endpoint<AddParticipantsRequest>
{
    private readonly IMediator _mediator;

    public AddParticipantsEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Post("/api/chat/conversations/{id}/participants");
        Claims("userId");
    }

    public override async Task HandleAsync(AddParticipantsRequest req, CancellationToken ct)
    {
        var id = Route<string>("id")!;
        var userId = User.FindFirst("userId")!.Value;
        await _mediator.Send(new AddParticipantsCommand(id, userId, req.UserIds), ct);
        await SendNoContentAsync(ct);
    }
}
