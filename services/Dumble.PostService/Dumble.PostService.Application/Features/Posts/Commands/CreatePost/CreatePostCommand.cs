using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.Application.Features.Posts.Commands.CreatePost;

public record CreatePostCommand(
    string? Content,
    string? GymId,
    List<string>? Hashtags,
    IReadOnlyList<UploadedImage>? Images
) : IRequest<PostResponse>;
