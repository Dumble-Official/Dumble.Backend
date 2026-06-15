using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Comments.Queries.ListCommentReactions;
using Dumble.PostService.Contracts.Reactions;

namespace Dumble.PostService.API.Endpoints.Comments;

public class ListCommentReactionsEndpoint : EndpointWithoutRequest<List<ReactionResponse>>
{
    private readonly IMediator _mediator;

    public ListCommentReactionsEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/comments/{commentId}/reactions/list");
        AllowAnonymous();
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var commentId = Route<Guid>("commentId");
        var offset = Query<int?>("offset", isRequired: false) ?? 0;
        var limit = Query<int?>("limit", isRequired: false) ?? 20;
        var result = await _mediator.Send(new ListCommentReactionsQuery(commentId, offset, limit), ct);
        await SendAsync(result, cancellation: ct);
    }
}
