using MediatR;
using Microsoft.AspNetCore.Http;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.Application.Features.Posts.Commands.CreatePost;

public record CreatePostCommand(
    string? Content,
    string? GymId,
    List<string>? Hashtags,
    IFormFileCollection? Images
) : IRequest<PostResponse>;
