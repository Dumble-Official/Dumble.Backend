using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Posts.Queries.GetPostsByHashtag;
using Dumble.PostService.Contracts.Common;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.API.Endpoints.Posts;

public class GetPostsByHashtagEndpoint : EndpointWithoutRequest<CursorPagedResponse<PostResponse>>
{
    private readonly IMediator _mediator;

    public GetPostsByHashtagEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/posts/hashtag/{tag}");
        AllowAnonymous();
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var tag = Route<string>("tag")!;
        var cursor = Query<string?>("cursor");
        var limit = Query<int?>("limit") ?? 20;

        var result = await _mediator.Send(new GetPostsByHashtagQuery(tag, cursor, limit), ct);
        await SendAsync(result, cancellation: ct);
    }
}
