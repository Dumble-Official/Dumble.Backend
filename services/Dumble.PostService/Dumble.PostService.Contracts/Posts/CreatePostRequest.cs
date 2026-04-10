using Microsoft.AspNetCore.Http;

namespace Dumble.PostService.Contracts.Posts;

public record CreatePostRequest(
    string? Content,
    string? GymId,
    List<string>? Hashtags,
    IFormFileCollection? Images
);
