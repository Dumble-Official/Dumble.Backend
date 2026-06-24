using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Posts.Queries.GetUserPostCount;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.API.Endpoints.Posts;

/// Total (non-deleted) post count for a user — drives the profile "Posts" stat.
public class GetUserPostCountEndpoint : EndpointWithoutRequest<PostCountResponse>
{
    private readonly IMediator _mediator;

    public GetUserPostCountEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/posts/user/{userId}/count");
        AllowAnonymous();
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var userId = Route<string>("userId")!;
        var result = await _mediator.Send(new GetUserPostCountQuery(userId), ct);
        await SendAsync(result, cancellation: ct);
    }
}
