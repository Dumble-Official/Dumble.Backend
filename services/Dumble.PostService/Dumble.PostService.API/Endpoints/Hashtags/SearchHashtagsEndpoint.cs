using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Hashtags.Queries.SearchHashtags;
using Dumble.PostService.Contracts.Hashtags;

namespace Dumble.PostService.API.Endpoints.Hashtags;

public class SearchHashtagsEndpoint : EndpointWithoutRequest<List<HashtagResponse>>
{
    private readonly IMediator _mediator;

    public SearchHashtagsEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/hashtags/search");
        AllowAnonymous();
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var query = Query<string>("q") ?? "";
        var limit = Query<int?>("limit", isRequired: false) ?? 20;
        var result = await _mediator.Send(new SearchHashtagsQuery(query, limit), ct);
        await SendAsync(result, cancellation: ct);
    }
}
