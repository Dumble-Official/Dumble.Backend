using FastEndpoints;
using MediatR;
using Dumble.ChatService.Application.Features.Conversations.Commands.UpdateConversation;
using Dumble.ChatService.Contracts.Conversations;

namespace Dumble.ChatService.API.Endpoints.Conversations;

public class UpdateConversationEndpoint : Endpoint<UpdateConversationRequest, ConversationResponse>
{
    private readonly IMediator _mediator;

    public UpdateConversationEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Put("/api/chat/conversations/{id}");
        Claims("userId");
    }

    public override async Task HandleAsync(UpdateConversationRequest req, CancellationToken ct)
    {
        var id = Route<string>("id")!;
        var userId = User.FindFirst("userId")!.Value;
        var result = await _mediator.Send(new UpdateConversationCommand(id, userId, req.Name, req.ImageUrl), ct);
        await SendAsync(result, cancellation: ct);
    }
}
