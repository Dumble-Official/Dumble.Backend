using MediatR;

namespace Dumble.ChatService.Application.Features.Blocks.Commands.SetBlock;

/// <summary>Block (Block=true) or unblock (Block=false) another user.</summary>
public sealed record SetBlockCommand(string BlockerId, string BlockedId, bool Block) : IRequest;
