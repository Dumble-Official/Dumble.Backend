using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Reactions.Commands.AddReaction;
using Dumble.PostService.Contracts.Reactions;

namespace Dumble.PostService.API.Endpoints.Reactions;

public class AddReactionEndpoint : Endpoint<AddReactionRequest, ReactionResponse>
{
    private readonly IMediator _mediator;

    public AddReactionEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Post("/api/posts/{postId}/reactions");
    }

    public override async Task HandleAsync(AddReactionRequest req, CancellationToken ct)
    {
        var postId = Route<Guid>("postId");
        var result = await _mediator.Send(new AddReactionCommand(postId, req.Type), ct);
        await SendAsync(result, cancellation: ct);
    }
}
