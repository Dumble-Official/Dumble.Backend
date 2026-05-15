using Dumble.ChatService.Application.Common;
using Dumble.ChatService.Application.Contracts;
using Dumble.ChatService.Contracts.Common;
using Dumble.ChatService.Contracts.Messages;
using Dumble.ChatService.Domain.Models;
using MediatR;

namespace Dumble.ChatService.Application.Features.Messages.Queries.GetMessages;

public class GetMessagesQueryHandler(
    IMessageRepository messageRepository,
    IConversationRepository conversationRepository
) : IRequestHandler<GetMessagesQuery, CursorPagedResponse<MessageResponse>>
{
    public async Task<CursorPagedResponse<MessageResponse>> Handle(
        GetMessagesQuery request, CancellationToken cancellationToken)
    {
        var conversation = await conversationRepository.GetByIdAsync(request.ConversationId, cancellationToken)
            ?? throw new KeyNotFoundException($"Conversation '{request.ConversationId}' not found.");

        if (!conversation.Participants.Any(p => p.UserId == request.CallerId))
            throw new UnauthorizedAccessException("You are not a participant in this conversation");

        var cursor = ChatCursorParsing.ParseUtc(request.Cursor);

        var messages = await messageRepository.GetByConversationIdAsync(
            request.ConversationId, cursor, request.Limit + 1, cancellationToken);

        var hasMore = messages.Count > request.Limit;
        if (hasMore) messages = messages.Take(request.Limit).ToList();

        var nextCursor = hasMore
            ? ChatCursorParsing.Format(messages[^1].CreatedAt)
            : null;

        var items = messages.Select(MapToResponse).ToList();

        return new CursorPagedResponse<MessageResponse>(items, nextCursor, hasMore);
    }

    private static MessageResponse MapToResponse(Message message) => new(
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
        message.CreatedAt);
}
