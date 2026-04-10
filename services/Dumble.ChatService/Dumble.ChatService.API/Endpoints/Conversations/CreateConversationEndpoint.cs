using FastEndpoints;
using MediatR;
using Dumble.ChatService.Application.Features.Conversations.Commands.CreateConversation;
using Dumble.ChatService.Contracts.Conversations;

namespace Dumble.ChatService.API.Endpoints.Conversations;

public class CreateConversationEndpoint : Endpoint<CreateConversationRequest, ConversationResponse>
{
    private readonly IMediator _mediator;

    public CreateConversationEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Post("/api/chat/conversations");
        Claims("userId");
    }

    public override async Task HandleAsync(CreateConversationRequest req, CancellationToken ct)
    {
        var userId = User.FindFirst("userId")!.Value;
        var displayName = User.FindFirst("displayName")?.Value ?? "User";
        var profileImage = User.FindFirst("profileImage")?.Value;

        var result = await _mediator.Send(new CreateConversationCommand(
            userId, displayName, profileImage, req.Type, req.Name, req.ParticipantIds), ct);

        await SendAsync(result, StatusCodes.Status201Created, ct);
    }
}
