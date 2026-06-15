using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Comments.Commands.SetCommentFlag;

namespace Dumble.PostService.API.Endpoints.Comments;

public class UnflagCommentEndpoint : EndpointWithoutRequest
{
    private readonly IMediator _mediator;

    public UnflagCommentEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Post("/api/comments/{commentId}/unflag");
        // Authenticated; the handler enforces Moderator/Admin.
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var commentId = Route<Guid>("commentId");
        await _mediator.Send(new SetCommentFlagCommand(commentId, Flagged: false), ct);
        await SendNoContentAsync(cancellation: ct);
    }
}
