using Dumble.ChatService.Contracts.Common;
using Dumble.ChatService.Contracts.Conversations;
using MediatR;

namespace Dumble.ChatService.Application.Features.Conversations.Queries.GetConversations;

public sealed record GetConversationsQuery(
    string UserId,
    string? Cursor,
    int Limit
) : IRequest<CursorPagedResponse<ConversationResponse>>;
