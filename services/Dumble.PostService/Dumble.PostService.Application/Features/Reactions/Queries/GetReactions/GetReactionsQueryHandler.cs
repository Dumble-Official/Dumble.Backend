using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Reactions;

namespace Dumble.PostService.Application.Features.Reactions.Queries.GetReactions;

public class GetReactionsQueryHandler : IRequestHandler<GetReactionsQuery, ReactionsSummaryResponse>
{
    private readonly IReactionRepository _reactionRepository;

    public GetReactionsQueryHandler(IReactionRepository reactionRepository)
    {
        _reactionRepository = reactionRepository;
    }

    public async Task<ReactionsSummaryResponse> Handle(GetReactionsQuery request, CancellationToken ct)
    {
        var counts = await _reactionRepository.GetCountsByPostIdAsync(request.PostId, ct);
        var totalCount = counts.Values.Sum();

        string? currentUserReaction = null;
        if (request.CurrentUserId is not null)
        {
            var userReaction = await _reactionRepository.GetByPostAndUserAsync(request.PostId, request.CurrentUserId, ct);
            currentUserReaction = userReaction?.Type.ToString();
        }

        return new ReactionsSummaryResponse(totalCount, counts, currentUserReaction);
    }
}
