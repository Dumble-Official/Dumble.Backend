using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Comments.Queries.GetComments;
using Dumble.PostService.Contracts.Comments;
using Dumble.PostService.Contracts.Common;

namespace Dumble.PostService.API.Endpoints.Comments;

public class GetCommentsEndpoint : EndpointWithoutRequest<CursorPagedResponse<CommentResponse>>
{
    private readonly IMediator _mediator;

    public GetCommentsEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/posts/{postId}/comments");
        AllowAnonymous();
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var postId = Route<Guid>("postId");
        var cursor = Query<string?>("cursor");
        var limit = Query<int?>("limit") ?? 20;

        var result = await _mediator.Send(new GetCommentsQuery(postId, cursor, limit), ct);
        await SendAsync(result, cancellation: ct);
    }
}
