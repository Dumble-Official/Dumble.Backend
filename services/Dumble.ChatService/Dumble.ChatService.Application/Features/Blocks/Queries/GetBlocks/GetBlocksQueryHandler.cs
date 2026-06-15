using Dumble.ChatService.Application.Contracts;
using MediatR;

namespace Dumble.ChatService.Application.Features.Blocks.Queries.GetBlocks;

public class GetBlocksQueryHandler(IBlockRepository blockRepository) : IRequestHandler<GetBlocksQuery, List<string>>
{
    public Task<List<string>> Handle(GetBlocksQuery request, CancellationToken cancellationToken)
        => blockRepository.GetBlockedIdsAsync(request.UserId, cancellationToken);
}
