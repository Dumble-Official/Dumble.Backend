using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Reactions.Queries.ListPostReactions;
using Dumble.PostService.Contracts.Reactions;

namespace Dumble.PostService.API.Endpoints.Reactions;

public class ListPostReactionsEndpoint : EndpointWithoutRequest<List<ReactionResponse>>
{
    private readonly IMediator _mediator;

    public ListPostReactionsEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/posts/{postId}/reactions/list");
        AllowAnonymous();
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var postId = Route<Guid>("postId");
        var offset = Query<int?>("offset", isRequired: false) ?? 0;
        var limit = Query<int?>("limit", isRequired: false) ?? 20;
        var result = await _mediator.Send(new ListPostReactionsQuery(postId, offset, limit), ct);
        await SendAsync(result, cancellation: ct);
    }
}
