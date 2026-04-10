using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Comments.Commands.UpdateComment;
using Dumble.PostService.Contracts.Comments;

namespace Dumble.PostService.API.Endpoints.Comments;

public class UpdateCommentEndpoint : Endpoint<UpdateCommentRequest, CommentResponse>
{
    private readonly IMediator _mediator;

    public UpdateCommentEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Put("/api/comments/{commentId}");
    }

    public override async Task HandleAsync(UpdateCommentRequest req, CancellationToken ct)
    {
        var commentId = Route<Guid>("commentId");
        var result = await _mediator.Send(new UpdateCommentCommand(commentId, req.Content), ct);
        await SendAsync(result, cancellation: ct);
    }
}
