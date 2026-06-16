using MediatR;

namespace Dumble.ChatService.Application.Features.Blocks.Queries.GetBlocks;

public sealed record GetBlocksQuery(string UserId) : IRequest<List<string>>;
