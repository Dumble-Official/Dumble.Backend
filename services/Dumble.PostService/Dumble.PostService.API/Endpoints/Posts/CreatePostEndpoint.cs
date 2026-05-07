using FastEndpoints;
using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Application.Features.Posts.Commands.CreatePost;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.API.Endpoints.Posts;

public class CreatePostEndpoint(IMediator mediator) : Endpoint<CreatePostRequest, PostResponse>
{
    public override void Configure()
    {
        Post("/api/posts");
        AllowFormData();
        AllowFileUploads();
    }

    public override async Task HandleAsync(CreatePostRequest req, CancellationToken ct)
    {
        var images = Files.Count == 0
            ? null
            : Files.Select(f => new UploadedImage(f.OpenReadStream(), f.FileName, f.ContentType)).ToList();

        var command = new CreatePostCommand(req.Content, req.GymId, req.Hashtags, images);
        var result = await mediator.Send(command, ct);
        await SendCreatedAtAsync<GetPostEndpoint>(new { id = result.Id }, result, cancellation: ct);
    }
}
