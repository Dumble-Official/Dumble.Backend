using Dumble.ChatService.Contracts.Common;
using Dumble.ChatService.Contracts.Messages;
using MediatR;

namespace Dumble.ChatService.Application.Features.Messages.Queries.GetMessages;

public sealed record GetMessagesQuery(
    string ConversationId,
    string? Cursor,
    int Limit
) : IRequest<CursorPagedResponse<MessageResponse>>;
