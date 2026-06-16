using Dumble.ChatService.Contracts.Messages;
using MediatR;

namespace Dumble.ChatService.Application.Features.Messages.Commands.SendMessage;

public sealed record SendMessageCommand(
    string ConversationId,
    string SenderId,
    string SenderName,
    string? SenderProfileImage,
    string Content,
    string? ReplyToMessageId,
    string? ImageUrl = null
) : IRequest<MessageResponse>;
