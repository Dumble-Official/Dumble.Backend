namespace Dumble.PostService.Contracts.Common;

public record CursorPagedResponse<T>(
    List<T> Items,
    string? NextCursor,
    bool HasMore
);
