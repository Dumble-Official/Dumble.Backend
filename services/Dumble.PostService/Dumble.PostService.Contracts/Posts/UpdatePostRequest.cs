namespace Dumble.PostService.Contracts.Posts;

public record UpdatePostRequest(
    string? Content,
    List<string>? Hashtags
);
