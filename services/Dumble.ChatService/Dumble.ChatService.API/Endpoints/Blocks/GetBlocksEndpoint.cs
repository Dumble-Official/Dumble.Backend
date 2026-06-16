using FastEndpoints;
using MediatR;
using Dumble.ChatService.Application.Features.Blocks.Queries.GetBlocks;

namespace Dumble.ChatService.API.Endpoints.Blocks;

public class GetBlocksEndpoint : EndpointWithoutRequest<List<string>>
{
    private readonly IMediator _mediator;

    public GetBlocksEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/chat/blocks");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var userId = User.FindFirst("userId")!.Value;
        var result = await _mediator.Send(new GetBlocksQuery(userId), ct);
        await SendAsync(result, cancellation: ct);
    }
}
