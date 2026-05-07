using Dumble.ChatService.Contracts.Conversations;
using MediatR;

namespace Dumble.ChatService.Application.Features.Conversations.Queries.GetConversation;

public sealed record GetConversationQuery(
    string ConversationId,
    string CallerId
) : IRequest<ConversationResponse>;
