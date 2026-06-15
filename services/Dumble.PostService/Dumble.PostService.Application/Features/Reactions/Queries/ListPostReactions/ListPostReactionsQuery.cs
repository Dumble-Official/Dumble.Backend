using MediatR;
using Dumble.PostService.Contracts.Reactions;

namespace Dumble.PostService.Application.Features.Reactions.Queries.ListPostReactions;

public record ListPostReactionsQuery(Guid PostId, int Offset, int Limit) : IRequest<List<ReactionResponse>>;
