using FastEndpoints;
using MediatR;
using Dumble.ChatService.Application.Features.Conversations.Commands.LeaveConversation;

namespace Dumble.ChatService.API.Endpoints.Conversations;

public class LeaveConversationEndpoint : EndpointWithoutRequest
{
    private readonly IMediator _mediator;

    public LeaveConversationEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Post("/api/chat/conversations/{id}/leave");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var conversationId = Route<string>("id")!;
        var userId = User.FindFirst("userId")!.Value;

        await _mediator.Send(new LeaveConversationCommand(conversationId, userId), ct);
        await SendNoContentAsync(ct);
    }
}
