using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Comments.Queries.GetComment;
using Dumble.PostService.Contracts.Comments;

namespace Dumble.PostService.API.Endpoints.Comments;

public class GetCommentEndpoint : EndpointWithoutRequest<CommentResponse>
{
    private readonly IMediator _mediator;

    public GetCommentEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/comments/{commentId}");
        AllowAnonymous();
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var commentId = Route<Guid>("commentId");
        var result = await _mediator.Send(new GetCommentQuery(commentId), ct);
        await SendAsync(result, cancellation: ct);
    }
}
