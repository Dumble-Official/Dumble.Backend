using Dumble.ChatService.Application.Contracts;
using MediatR;

namespace Dumble.ChatService.Application.Features.Blocks.Commands.SetBlock;

public class SetBlockCommandHandler(IBlockRepository blockRepository) : IRequestHandler<SetBlockCommand>
{
    public async Task Handle(SetBlockCommand request, CancellationToken cancellationToken)
    {
        if (request.BlockerId == request.BlockedId)
            throw new ArgumentException("You cannot block yourself");

        if (request.Block)
            await blockRepository.BlockAsync(request.BlockerId, request.BlockedId, cancellationToken);
        else
            await blockRepository.UnblockAsync(request.BlockerId, request.BlockedId, cancellationToken);
    }
}
