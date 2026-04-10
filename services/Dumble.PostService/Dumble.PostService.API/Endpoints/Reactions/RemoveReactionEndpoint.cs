using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Reactions.Commands.RemoveReaction;

namespace Dumble.PostService.API.Endpoints.Reactions;

public class RemoveReactionEndpoint : EndpointWithoutRequest
{
    private readonly IMediator _mediator;

    public RemoveReactionEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Delete("/api/posts/{postId}/reactions");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var postId = Route<Guid>("postId");
        await _mediator.Send(new RemoveReactionCommand(postId), ct);
        await SendNoContentAsync(cancellation: ct);
    }
}
