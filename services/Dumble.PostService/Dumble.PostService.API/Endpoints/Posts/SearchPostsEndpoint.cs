using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Posts.Queries.SearchPosts;
using Dumble.PostService.Contracts.Common;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.API.Endpoints.Posts;

public class SearchPostsEndpoint : EndpointWithoutRequest<CursorPagedResponse<PostResponse>>
{
    private readonly IMediator _mediator;

    public SearchPostsEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/posts/search");
        AllowAnonymous();
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var query = Query<string>("q") ?? "";
        var cursor = Query<string?>("cursor", isRequired: false);
        var limit = Math.Clamp(Query<int?>("limit", isRequired: false) ?? 20, 1, 100);

        var result = await _mediator.Send(new SearchPostsQuery(query, cursor, limit), ct);
        await SendAsync(result, cancellation: ct);
    }
}
