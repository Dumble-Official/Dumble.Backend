using FastEndpoints;
using MediatR;
using Dumble.ChatService.Application.Features.Messages.Commands.SendMessage;
using Dumble.ChatService.Contracts.Messages;

namespace Dumble.ChatService.API.Endpoints.Messages;

public class SendMessageEndpoint : Endpoint<SendMessageRequest, MessageResponse>
{
    private readonly IMediator _mediator;

    public SendMessageEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Post("/api/chat/conversations/{id}/messages");
        Claims("userId");
    }

    public override async Task HandleAsync(SendMessageRequest req, CancellationToken ct)
    {
        var conversationId = Route<string>("id")!;
        var userId = User.FindFirst("userId")!.Value;
        var displayName = User.FindFirst("displayName")?.Value ?? "User";
        var profileImage = User.FindFirst("profileImage")?.Value;

        var result = await _mediator.Send(new SendMessageCommand(
            conversationId, userId, displayName, profileImage, req.Content, req.ReplyToMessageId, req.ImageUrl), ct);

        await SendAsync(result, StatusCodes.Status201Created, ct);
    }
}
