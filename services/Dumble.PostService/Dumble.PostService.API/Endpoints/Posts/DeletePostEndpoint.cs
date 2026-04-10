using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Posts.Commands.DeletePost;

namespace Dumble.PostService.API.Endpoints.Posts;

public class DeletePostEndpoint : EndpointWithoutRequest
{
    private readonly IMediator _mediator;

    public DeletePostEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Delete("/api/posts/{id}");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var id = Route<Guid>("id");
        await _mediator.Send(new DeletePostCommand(id), ct);
        await SendNoContentAsync(cancellation: ct);
    }
}
