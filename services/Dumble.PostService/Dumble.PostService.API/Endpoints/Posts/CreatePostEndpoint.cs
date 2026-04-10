using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Features.Posts.Commands.CreatePost;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.API.Endpoints.Posts;

public class CreatePostEndpoint : Endpoint<CreatePostRequest, PostResponse>
{
    private readonly IMediator _mediator;

    public CreatePostEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Post("/api/posts");
        AllowFormData();
        AllowFileUploads();
    }

    public override async Task HandleAsync(CreatePostRequest req, CancellationToken ct)
    {
        var command = new CreatePostCommand(req.Content, req.GymId, req.Hashtags, Files);
        var result = await _mediator.Send(command, ct);
        await SendCreatedAtAsync<GetPostEndpoint>(new { id = result.Id }, result, cancellation: ct);
    }
}
