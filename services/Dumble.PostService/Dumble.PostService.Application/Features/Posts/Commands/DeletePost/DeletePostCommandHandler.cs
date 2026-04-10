using MediatR;
using MassTransit;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Domain.Enums;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Events.Posts;

namespace Dumble.PostService.Application.Features.Posts.Commands.DeletePost;

public class DeletePostCommandHandler : IRequestHandler<DeletePostCommand>
{
    private readonly IPostRepository _postRepository;
    private readonly IFileService _fileService;
    private readonly ILoggedInUserService _userService;
    private readonly IPublishEndpoint _publishEndpoint;

    public DeletePostCommandHandler(
        IPostRepository postRepository,
        IFileService fileService,
        ILoggedInUserService userService,
        IPublishEndpoint publishEndpoint)
    {
        _postRepository = postRepository;
        _fileService = fileService;
        _userService = userService;
        _publishEndpoint = publishEndpoint;
    }

    public async Task Handle(DeletePostCommand request, CancellationToken ct)
    {
        var currentUser = await _userService.GetCurrentUserAsync(ct);
        var post = await _postRepository.GetByIdWithDetailsAsync(request.PostId, ct)
            ?? throw new KeyNotFoundException($"Post {request.PostId} not found");

        if (post.AuthorId != currentUser.Id)
            throw new UnauthorizedAccessException("You can only delete your own posts");

        post.Status = PostStatus.Deleted;
        post.UpdatedAt = DateTime.UtcNow;
        await _postRepository.UpdateAsync(post, ct);

        // Delete images from Cloudinary
        foreach (var image in post.Images)
        {
            await _fileService.DeleteAsync(image.PublicId, ct);
        }

        await _publishEndpoint.Publish(new PostDeletedEvent(
            post.Id.ToString(),
            post.AuthorId
        ), ct);
    }
}
