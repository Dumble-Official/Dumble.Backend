using MediatR;
using Dumble.PostService.Contracts.Comments;

namespace Dumble.PostService.Application.Features.Comments.Queries.GetComment;

public record GetCommentQuery(Guid CommentId) : IRequest<CommentResponse>;
