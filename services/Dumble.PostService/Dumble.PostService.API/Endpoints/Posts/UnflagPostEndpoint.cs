using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Posts.Commands.SetPostFlag;

namespace Dumble.PostService.API.Endpoints.Posts;

public class UnflagPostEndpoint : EndpointWithoutRequest
{
    private readonly IMediator _mediator;

    public UnflagPostEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Post("/api/posts/{id}/unflag");
        // Authenticated; the handler enforces Moderator/Admin.
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var id = Route<Guid>("id");
        await _mediator.Send(new SetPostFlagCommand(id, Flagged: false), ct);
        await SendNoContentAsync(cancellation: ct);
    }
}
