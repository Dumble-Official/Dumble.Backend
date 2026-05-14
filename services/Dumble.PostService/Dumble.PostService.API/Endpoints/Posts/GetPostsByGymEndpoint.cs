using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Posts.Queries.GetPostsByGym;
using Dumble.PostService.Contracts.Common;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.API.Endpoints.Posts;

public class GetPostsByGymEndpoint : EndpointWithoutRequest<CursorPagedResponse<PostResponse>>
{
    private readonly IMediator _mediator;

    public GetPostsByGymEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/posts/gym/{gymId}");
        AllowAnonymous();
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var gymId = Route<string>("gymId")!;
        var cursor = Query<string?>("cursor", isRequired: false);
        var limit = Math.Clamp(Query<int?>("limit", isRequired: false) ?? 20, 1, 100);

        var result = await _mediator.Send(new GetPostsByGymQuery(gymId, cursor, limit), ct);
        await SendAsync(result, cancellation: ct);
    }
}
