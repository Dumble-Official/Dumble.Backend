using MediatR;

namespace Dumble.ChatService.Application.Features.Conversations.Commands.LeaveConversation;

public sealed record LeaveConversationCommand(
    string ConversationId,
    string UserId
) : IRequest;
