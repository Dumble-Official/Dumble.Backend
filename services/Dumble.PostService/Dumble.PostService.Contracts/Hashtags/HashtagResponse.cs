namespace Dumble.PostService.Contracts.Hashtags;

public record HashtagResponse(
    Guid Id,
    string Name,
    int UsageCount
);
