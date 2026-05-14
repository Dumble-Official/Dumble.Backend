using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Comments.Queries.GetReplies;
using Dumble.PostService.Contracts.Comments;
using Dumble.PostService.Contracts.Common;

namespace Dumble.PostService.API.Endpoints.Comments;

public class GetRepliesEndpoint : EndpointWithoutRequest<CursorPagedResponse<CommentResponse>>
{
    private readonly IMediator _mediator;

    public GetRepliesEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/comments/{commentId}/replies");
        AllowAnonymous();
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var commentId = Route<Guid>("commentId");
        var cursor = Query<string?>("cursor", isRequired: false);
        var limit = Math.Clamp(Query<int?>("limit", isRequired: false) ?? 20, 1, 100);

        var result = await _mediator.Send(new GetRepliesQuery(commentId, cursor, limit), ct);
        await SendAsync(result, cancellation: ct);
    }
}
