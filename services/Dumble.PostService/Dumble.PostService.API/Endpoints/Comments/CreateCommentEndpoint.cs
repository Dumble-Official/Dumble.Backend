using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Comments.Commands.CreateComment;
using Dumble.PostService.Contracts.Comments;

namespace Dumble.PostService.API.Endpoints.Comments;

public class CreateCommentEndpoint : Endpoint<CreateCommentRequest, CommentResponse>
{
    private readonly IMediator _mediator;

    public CreateCommentEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Post("/api/posts/{postId}/comments");
    }

    public override async Task HandleAsync(CreateCommentRequest req, CancellationToken ct)
    {
        var postId = Route<Guid>("postId");
        var command = new CreateCommentCommand(postId, req.Content, req.ParentCommentId);
        var result = await _mediator.Send(command, ct);
        await SendAsync(result, 201, cancellation: ct);
    }
}
