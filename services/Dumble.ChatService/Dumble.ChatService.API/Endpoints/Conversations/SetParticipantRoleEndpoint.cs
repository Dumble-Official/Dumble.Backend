using FastEndpoints;
using MediatR;
using Dumble.ChatService.Application.Features.Conversations.Commands.SetParticipantRole;
using Dumble.ChatService.Contracts.Conversations;

namespace Dumble.ChatService.API.Endpoints.Conversations;

public class SetParticipantRoleEndpoint : Endpoint<SetParticipantRoleRequest>
{
    private readonly IMediator _mediator;

    public SetParticipantRoleEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Put("/api/chat/conversations/{id}/participants/{userId}/role");
        Claims("userId");
    }

    public override async Task HandleAsync(SetParticipantRoleRequest req, CancellationToken ct)
    {
        var conversationId = Route<string>("id")!;
        var targetUserId = Route<string>("userId")!;
        var callerId = User.FindFirst("userId")!.Value;

        await _mediator.Send(new SetParticipantRoleCommand(conversationId, callerId, targetUserId, req.Role), ct);
        await SendNoContentAsync(ct);
    }
}
