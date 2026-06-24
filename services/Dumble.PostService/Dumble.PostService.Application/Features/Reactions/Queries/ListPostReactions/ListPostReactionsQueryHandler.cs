using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Reactions;

namespace Dumble.PostService.Application.Features.Reactions.Queries.ListPostReactions;

public class ListPostReactionsQueryHandler : IRequestHandler<ListPostReactionsQuery, List<ReactionResponse>>
{
    private readonly IReactionRepository _reactionRepository;

    public ListPostReactionsQueryHandler(IReactionRepository reactionRepository)
    {
        _reactionRepository = reactionRepository;
    }

    public async Task<List<ReactionResponse>> Handle(ListPostReactionsQuery request, CancellationToken ct)
    {
        var limit = Math.Clamp(request.Limit, 1, 100);
        var offset = Math.Max(0, request.Offset);
        var reactions = await _reactionRepository.GetByPostIdAsync(request.PostId, offset, limit, ct);
        return reactions
            .Select(r => new ReactionResponse(r.Id, r.UserId, r.DisplayName, r.ProfileImage, r.Type.ToString(), r.CreatedAt))
            .ToList();
    }
}
