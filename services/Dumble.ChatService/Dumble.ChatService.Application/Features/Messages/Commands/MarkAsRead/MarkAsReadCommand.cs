using MediatR;

namespace Dumble.ChatService.Application.Features.Messages.Commands.MarkAsRead;

public sealed record MarkAsReadCommand(
    string ConversationId,
    string UserId,
    string MessageId
) : IRequest;
