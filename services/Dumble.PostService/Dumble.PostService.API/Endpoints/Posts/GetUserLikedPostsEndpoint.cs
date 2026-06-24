using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Posts.Queries.GetUserLikedPosts;
using Dumble.PostService.Contracts.Common;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.API.Endpoints.Posts;

public class GetUserLikedPostsEndpoint : EndpointWithoutRequest<CursorPagedResponse<PostResponse>>
{
    private readonly IMediator _mediator;

    public GetUserLikedPostsEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/posts/user/{userId}/liked");
        AllowAnonymous();
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var userId = Route<string>("userId")!;
        var cursor = Query<string?>("cursor", isRequired: false);
        var limit = Math.Clamp(Query<int?>("limit", isRequired: false) ?? 20, 1, 100);

        var result = await _mediator.Send(new GetUserLikedPostsQuery(userId, cursor, limit), ct);
        await SendAsync(result, cancellation: ct);
    }
}
