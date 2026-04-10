using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Posts.Queries.GetPost;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.API.Endpoints.Posts;

public class GetPostEndpoint : EndpointWithoutRequest<PostResponse>
{
    private readonly IMediator _mediator;

    public GetPostEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/posts/{id}");
        AllowAnonymous();
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var id = Route<Guid>("id");
        var result = await _mediator.Send(new GetPostQuery(id), ct);
        await SendAsync(result, cancellation: ct);
    }
}
