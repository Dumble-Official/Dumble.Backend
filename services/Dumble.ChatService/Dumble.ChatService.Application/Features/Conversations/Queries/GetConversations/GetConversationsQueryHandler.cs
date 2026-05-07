using Dumble.ChatService.Application.Common;
using Dumble.ChatService.Application.Contracts;
using Dumble.ChatService.Contracts.Common;
using Dumble.ChatService.Contracts.Conversations;
using Dumble.ChatService.Domain.Models;
using MediatR;

namespace Dumble.ChatService.Application.Features.Conversations.Queries.GetConversations;

public class GetConversationsQueryHandler(
    IConversationRepository conversationRepository
) : IRequestHandler<GetConversationsQuery, CursorPagedResponse<ConversationResponse>>
{
    public async Task<CursorPagedResponse<ConversationResponse>> Handle(
        GetConversationsQuery request, CancellationToken cancellationToken)
    {
        var cursor = ChatCursorParsing.ParseUtc(request.Cursor);

        var conversations = await conversationRepository.GetByUserIdAsync(
            request.UserId, cursor, request.Limit + 1, cancellationToken);

        var hasMore = conversations.Count > request.Limit;

        if (hasMore)
            conversations = conversations.Take(request.Limit).ToList();

        var nextCursor = hasMore
            ? ChatCursorParsing.Format(conversations[^1].UpdatedAt)
            : null;

        var items = conversations.Select(MapToResponse).ToList();

        return new CursorPagedResponse<ConversationResponse>(items, nextCursor, hasMore);
    }

    private static ConversationResponse MapToResponse(Conversation conversation)
    {
        return new ConversationResponse(
            conversation.Id,
            conversation.Type,
            conversation.Name,
            conversation.ImageUrl,
            conversation.Participants.Select(p => new ParticipantResponse(
                p.UserId, p.DisplayName, p.ProfileImage, p.Role, p.JoinedAt
            )).ToList(),
            conversation.LastMessage is not null
                ? new LastMessageResponse(
                    conversation.LastMessage.MessageId,
                    conversation.LastMessage.SenderId,
                    conversation.LastMessage.SenderName,
                    conversation.LastMessage.Content,
                    conversation.LastMessage.SentAt)
                : null,
            conversation.CreatedAt,
            conversation.UpdatedAt
        );
    }
}
