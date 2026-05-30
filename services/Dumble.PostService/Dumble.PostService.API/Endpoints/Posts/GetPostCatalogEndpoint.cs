using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Posts.Queries.GetPostCatalog;
using Dumble.PostService.Contracts.Common;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.API.Endpoints.Posts;

public class GetPostCatalogEndpoint : EndpointWithoutRequest<CursorPagedResponse<PostCatalogItem>>
{
    private readonly IMediator _mediator;

    public GetPostCatalogEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    private const int MaxPageSize = 200;

    public override void Configure()
    {
        Get("/api/posts/catalog");
        // Authenticated by default. This is a full-table sweep meant for trusted backend
        // callers (the recommendation reconcile job, which presents a service token), so
        // there is no AllowAnonymous — a valid token is required to page the whole catalog.
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var cursor = Query<string?>("cursor", isRequired: false);
        var limit = Math.Clamp(Query<int?>("limit", isRequired: false) ?? 100, 1, MaxPageSize);

        var result = await _mediator.Send(new GetPostCatalogQuery(cursor, limit), ct);
        await SendAsync(result, cancellation: ct);
    }
}
