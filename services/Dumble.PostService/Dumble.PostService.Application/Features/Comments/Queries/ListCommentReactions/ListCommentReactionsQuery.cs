using MediatR;
using Dumble.PostService.Contracts.Reactions;

namespace Dumble.PostService.Application.Features.Comments.Queries.ListCommentReactions;

public record ListCommentReactionsQuery(Guid CommentId, int Offset, int Limit) : IRequest<List<ReactionResponse>>;
