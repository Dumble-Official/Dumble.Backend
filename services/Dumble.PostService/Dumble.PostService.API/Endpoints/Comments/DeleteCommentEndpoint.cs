using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Comments.Commands.DeleteComment;

namespace Dumble.PostService.API.Endpoints.Comments;

public class DeleteCommentEndpoint : EndpointWithoutRequest
{
    private readonly IMediator _mediator;

    public DeleteCommentEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Delete("/api/comments/{commentId}");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var commentId = Route<Guid>("commentId");
        await _mediator.Send(new DeleteCommentCommand(commentId), ct);
        await SendNoContentAsync(cancellation: ct);
    }
}
