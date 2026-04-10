namespace Dumble.PostService.Contracts.Posts;

public record BatchGetPostsRequest(
    List<Guid> Ids
);
