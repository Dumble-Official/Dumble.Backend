using FastEndpoints;
using MediatR;
using Dumble.ChatService.Application.Features.Blocks.Commands.SetBlock;

namespace Dumble.ChatService.API.Endpoints.Blocks;

public class UnblockUserEndpoint : EndpointWithoutRequest
{
    private readonly IMediator _mediator;

    public UnblockUserEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Delete("/api/chat/blocks/{userId}");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var blockedId = Route<string>("userId")!;
        var blockerId = User.FindFirst("userId")!.Value;
        await _mediator.Send(new SetBlockCommand(blockerId, blockedId, Block: false), ct);
        await SendNoContentAsync(ct);
    }
}
