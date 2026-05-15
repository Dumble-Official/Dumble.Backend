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

    private const int MaxIdsPerBatch = 100;

    public override void Configure()
    {
        Post("/api/posts/batch");
        // Authenticated by default — used by SocialService's feed assembly.
        // No AllowAnonymous so harvesting requires a valid token, and rate
        // limiting can attribute calls to a userId.
    }

    public override async Task HandleAsync(BatchGetPostsRequest req, CancellationToken ct)
    {
        if (req?.Ids == null || req.Ids.Count == 0)
        {
            await SendAsync(new List<Dumble.PostService.Contracts.Posts.PostResponse>(), cancellation: ct);
            return;
        }
        if (req.Ids.Count > MaxIdsPerBatch)
        {
            ThrowError($"Cannot request more than {MaxIdsPerBatch} posts per batch");
        }
        var result = await _mediator.Send(new BatchGetPostsQuery(req.Ids), ct);
        await SendAsync(result, cancellation: ct);
    }
}
