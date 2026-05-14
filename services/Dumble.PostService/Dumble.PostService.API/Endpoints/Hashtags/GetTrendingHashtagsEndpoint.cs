using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Hashtags.Queries.GetTrending;
using Dumble.PostService.Contracts.Hashtags;

namespace Dumble.PostService.API.Endpoints.Hashtags;

public class GetTrendingHashtagsEndpoint : EndpointWithoutRequest<List<HashtagResponse>>
{
    private readonly IMediator _mediator;

    public GetTrendingHashtagsEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/hashtags/trending");
        AllowAnonymous();
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var limit = Math.Clamp(Query<int?>("limit", isRequired: false) ?? 20, 1, 100);
        var result = await _mediator.Send(new GetTrendingHashtagsQuery(limit), ct);
        await SendAsync(result, cancellation: ct);
    }
}
