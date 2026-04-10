using MediatR;
using Dumble.PostService.Contracts.Comments;
using Dumble.PostService.Contracts.Common;

namespace Dumble.PostService.Application.Features.Comments.Queries.GetComments;

public record GetCommentsQuery(Guid PostId, string? Cursor, int Limit = 20)
    : IRequest<CursorPagedResponse<CommentResponse>>;
