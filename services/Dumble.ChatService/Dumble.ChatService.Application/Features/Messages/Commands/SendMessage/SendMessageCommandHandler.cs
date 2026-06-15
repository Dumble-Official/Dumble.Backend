using Dumble.ChatService.Application.Contracts;
using Dumble.ChatService.Contracts.Messages;
using Dumble.ChatService.Domain.Models;
using Dumble.SharedKernel.Events.Chat;
using MassTransit;
using MediatR;

namespace Dumble.ChatService.Application.Features.Messages.Commands.SendMessage;

public class SendMessageCommandHandler(
    IMessageRepository messageRepository,
    IConversationRepository conversationRepository,
    IChatHubService chatHubService,
    IPublishEndpoint publishEndpoint
) : IRequestHandler<SendMessageCommand, MessageResponse>
{
    public async Task<MessageResponse> Handle(SendMessageCommand request, CancellationToken cancellationToken)
    {
        var conversation = await conversationRepository.GetByIdAsync(
            request.ConversationId, cancellationToken)
            ?? throw new KeyNotFoundException($"Conversation '{request.ConversationId}' not found.");

        if (!conversation.Participants.Any(p => p.UserId == request.SenderId))
            throw new UnauthorizedAccessException("You are not a participant in this conversation");

        var now = DateTime.UtcNow;

        var message = new Message
        {
            ConversationId = request.ConversationId,
            SenderId = request.SenderId,
            SenderName = request.SenderName,
            SenderProfileImage = request.SenderProfileImage,
            Content = request.Content,
            ImageUrl = request.ImageUrl,
            MessageType = string.IsNullOrWhiteSpace(request.ImageUrl) ? "Text" : "Image",
            ReplyToMessageId = request.ReplyToMessageId,
            IsDeleted = false,
            CreatedAt = now
        };

        await messageRepository.CreateAsync(message, cancellationToken);

        var lastMessage = new LastMessageInfo
        {
            MessageId = message.Id,
            SenderId = request.SenderId,
            SenderName = request.SenderName,
            Content = request.Content,
            SentAt = now
        };

        await conversationRepository.UpdateLastMessageAsync(
            request.ConversationId, lastMessage, cancellationToken);

        var recipientIds = conversation.Participants
            .Where(p => p.UserId != request.SenderId)
            .Select(p => p.UserId)
            .ToList();

        var preview = request.Content.Length > 100
            ? request.Content[..100] + "..."
            : request.Content;

        await publishEndpoint.Publish(new MessageSentEvent(
            request.ConversationId,
            request.SenderId,
            request.SenderName,
            request.SenderProfileImage,
            recipientIds,
            preview,
            new DateTimeOffset(now, TimeSpan.Zero)
        ), cancellationToken);

        var response = MapToResponse(message);

        await chatHubService.SendMessageAsync(request.ConversationId, response, cancellationToken);

        return response;
    }

    private static MessageResponse MapToResponse(Message message)
    {
        return new MessageResponse(
            message.Id,
            message.ConversationId,
            message.SenderId,
            message.SenderName,
            message.SenderProfileImage,
            message.Content,
            message.MessageType,
            message.ImageUrl,
            message.ReplyToMessageId,
            message.IsDeleted,
            message.CreatedAt
        );
    }
}
