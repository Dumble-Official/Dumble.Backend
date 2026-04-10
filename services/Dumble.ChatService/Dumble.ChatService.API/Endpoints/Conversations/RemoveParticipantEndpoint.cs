using FastEndpoints;
using MediatR;
using Dumble.ChatService.Application.Features.Conversations.Commands.RemoveParticipant;

namespace Dumble.ChatService.API.Endpoints.Conversations;

public class RemoveParticipantEndpoint : EndpointWithoutRequest
{
    private readonly IMediator _mediator;

    public RemoveParticipantEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Delete("/api/chat/conversations/{id}/participants/{userId}");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var conversationId = Route<string>("id")!;
        var userId = Route<string>("userId")!;

        await _mediator.Send(new RemoveParticipantCommand(conversationId, userId), ct);
        await SendNoContentAsync(ct);
    }
}
