namespace Dumble.PostService.Contracts.Common;

public record CursorPagedRequest(
    string? Cursor,
    int Limit = 20
);
