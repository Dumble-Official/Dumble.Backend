using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Comments.Commands.AddCommentReaction;
using Dumble.PostService.Contracts.Reactions;

namespace Dumble.PostService.API.Endpoints.Comments;

public class AddCommentReactionEndpoint : Endpoint<AddReactionRequest, ReactionResponse>
{
    private readonly IMediator _mediator;

    public AddCommentReactionEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Post("/api/comments/{commentId}/reactions");
    }

    public override async Task HandleAsync(AddReactionRequest req, CancellationToken ct)
    {
        var commentId = Route<Guid>("commentId");
        var result = await _mediator.Send(new AddCommentReactionCommand(commentId, req.Type), ct);
        await SendAsync(result, cancellation: ct);
    }
}
