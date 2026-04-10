using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Posts.Queries.BatchGetPosts;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.API.Endpoints.Posts;

public class BatchGetPostsEndpoint : Endpoint<BatchGetPostsRequest, List<PostResponse>>
{
    private readonly IMediator _mediator;

    public BatchGetPostsEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Post("/api/posts/batch");
        AllowAnonymous();
    }

    public override async Task HandleAsync(BatchGetPostsRequest req, CancellationToken ct)
    {
        var result = await _mediator.Send(new BatchGetPostsQuery(req.Ids), ct);
        await SendAsync(result, cancellation: ct);
    }
}
