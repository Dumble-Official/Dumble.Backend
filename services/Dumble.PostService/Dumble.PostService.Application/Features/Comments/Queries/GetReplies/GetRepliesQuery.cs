using MediatR;
using Dumble.PostService.Contracts.Comments;
using Dumble.PostService.Contracts.Common;

namespace Dumble.PostService.Application.Features.Comments.Queries.GetReplies;

public record GetRepliesQuery(Guid ParentCommentId, string? Cursor, int Limit = 20)
    : IRequest<CursorPagedResponse<CommentResponse>>;
