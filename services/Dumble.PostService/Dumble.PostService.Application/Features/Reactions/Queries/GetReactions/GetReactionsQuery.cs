using MediatR;
using Dumble.PostService.Contracts.Reactions;

namespace Dumble.PostService.Application.Features.Reactions.Queries.GetReactions;

public record GetReactionsQuery(Guid PostId, string? CurrentUserId) : IRequest<ReactionsSummaryResponse>;
