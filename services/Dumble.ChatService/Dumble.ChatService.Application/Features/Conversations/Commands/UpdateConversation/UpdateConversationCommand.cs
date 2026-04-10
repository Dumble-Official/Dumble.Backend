using Dumble.ChatService.Contracts.Conversations;
using MediatR;

namespace Dumble.ChatService.Application.Features.Conversations.Commands.UpdateConversation;

public sealed record UpdateConversationCommand(
    string ConversationId,
    string? Name,
    string? ImageUrl
) : IRequest<ConversationResponse>;
