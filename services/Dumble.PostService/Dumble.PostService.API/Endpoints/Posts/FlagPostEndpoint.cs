using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Posts.Commands.SetPostFlag;

namespace Dumble.PostService.API.Endpoints.Posts;

public class FlagPostEndpoint : EndpointWithoutRequest
{
    private readonly IMediator _mediator;

    public FlagPostEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Post("/api/posts/{id}/flag");
        // Authenticated; the handler enforces Moderator/Admin.
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var id = Route<Guid>("id");
        await _mediator.Send(new SetPostFlagCommand(id, Flagged: true), ct);
        await SendNoContentAsync(cancellation: ct);
    }
}
