using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Posts.Commands.UpdatePost;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.API.Endpoints.Posts;

public class UpdatePostEndpoint : Endpoint<UpdatePostRequest, PostResponse>
{
    private readonly IMediator _mediator;

    public UpdatePostEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Put("/api/posts/{id}");
    }

    public override async Task HandleAsync(UpdatePostRequest req, CancellationToken ct)
    {
        var id = Route<Guid>("id");
        var command = new UpdatePostCommand(id, req.Content, req.Hashtags);
        var result = await _mediator.Send(command, ct);
        await SendAsync(result, cancellation: ct);
    }
}
