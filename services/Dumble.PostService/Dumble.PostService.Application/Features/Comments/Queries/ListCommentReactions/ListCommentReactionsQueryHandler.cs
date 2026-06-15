using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Reactions;

namespace Dumble.PostService.Application.Features.Comments.Queries.ListCommentReactions;

public class ListCommentReactionsQueryHandler : IRequestHandler<ListCommentReactionsQuery, List<ReactionResponse>>
{
    private readonly ICommentReactionRepository _commentReactionRepository;

    public ListCommentReactionsQueryHandler(ICommentReactionRepository commentReactionRepository)
    {
        _commentReactionRepository = commentReactionRepository;
    }

    public async Task<List<ReactionResponse>> Handle(ListCommentReactionsQuery request, CancellationToken ct)
    {
        var limit = Math.Clamp(request.Limit, 1, 100);
        var offset = Math.Max(0, request.Offset);
        var reactions = await _commentReactionRepository.GetByCommentIdAsync(request.CommentId, offset, limit, ct);
        return reactions
            .Select(r => new ReactionResponse(r.Id, r.UserId, r.Type.ToString(), r.CreatedAt))
            .ToList();
    }
}
