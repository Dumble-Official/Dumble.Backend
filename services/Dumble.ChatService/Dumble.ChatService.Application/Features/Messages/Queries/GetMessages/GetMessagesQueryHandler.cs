using Dumble.ChatService.Application.Contracts;
using Dumble.ChatService.Contracts.Common;
using Dumble.ChatService.Contracts.Messages;
using Dumble.ChatService.Domain.Models;
using MediatR;

namespace Dumble.ChatService.Application.Features.Messages.Queries.GetMessages;

public class GetMessagesQueryHandler(
    IMessageRepository messageRepository
) : IRequestHandler<GetMessagesQuery, CursorPagedResponse<MessageResponse>>
{
    public async Task<CursorPagedResponse<MessageResponse>> Handle(
        GetMessagesQuery request, CancellationToken cancellationToken)
    {
        DateTime? cursor = request.Cursor is not null
            ? DateTime.Parse(request.Cursor)
            : null;

        var messages = await messageRepository.GetByConversationIdAsync(
            request.ConversationId, cursor, request.Limit + 1, cancellationToken);

        var hasMore = messages.Count > request.Limit;

        if (hasMore)
            messages = messages.Take(request.Limit).ToList();

        string? nextCursor = hasMore
            ? messages[^1].CreatedAt.ToString("O")
            : null;

        var items = messages.Select(MapToResponse).ToList();

        return new CursorPagedResponse<MessageResponse>(items, nextCursor, hasMore);
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
