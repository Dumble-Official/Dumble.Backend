using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Comments.Commands.RemoveCommentReaction;

namespace Dumble.PostService.API.Endpoints.Comments;

public class RemoveCommentReactionEndpoint : EndpointWithoutRequest
{
    private readonly IMediator _mediator;

    public RemoveCommentReactionEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Delete("/api/comments/{commentId}/reactions");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var commentId = Route<Guid>("commentId");
        await _mediator.Send(new RemoveCommentReactionCommand(commentId), ct);
        await SendNoContentAsync(cancellation: ct);
    }
}
