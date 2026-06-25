using Dumble.ChatService.Application.Common;
using Dumble.ChatService.Application.Contracts;
using Dumble.ChatService.Contracts.Common;
using Dumble.ChatService.Contracts.Conversations;
using Dumble.ChatService.Domain.Models;
using MediatR;

namespace Dumble.ChatService.Application.Features.Conversations.Queries.GetConversations;

public class GetConversationsQueryHandler(
    IConversationRepository conversationRepository,
    IMessageRepository messageRepository
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

        // Resolve any placeholder participant names from message history and persist the
        // correction, so peers show their real name/photo even in conversations created
        // before the sender-upsert existed (self-heals; the lookup only runs while stale).
        foreach (var conversation in conversations)
        {
            if (await ParticipantBackfill.ResolveSentinelNamesAsync(
                    conversation, messageRepository, cancellationToken))
                await conversationRepository.UpdateAsync(conversation, cancellationToken);
        }

        // Compute each conversation's unread count for the requesting user in
        // parallel (one indexed count per conversation, page-bounded).
        var counts = await Task.WhenAll(conversations.Select(async c =>
        {
            var me = c.Participants.FirstOrDefault(p => p.UserId == request.UserId);
            var count = await messageRepository.CountUnreadAsync(
                c.Id, me?.LastReadMessageId, request.UserId, cancellationToken);
            return (c.Id, count);
        }));
        var unreadByConversation = counts.ToDictionary(x => x.Id, x => x.count);

        var items = conversations
            .Select(c => MapToResponse(
                c,
                unreadByConversation.TryGetValue(c.Id, out var u) ? (int)u : 0))
            .ToList();

        return new CursorPagedResponse<ConversationResponse>(items, nextCursor, hasMore);
    }

    private static ConversationResponse MapToResponse(Conversation conversation, int unreadCount)
    {
        return new ConversationResponse(
            conversation.Id,
            conversation.Type,
            conversation.Name,
            conversation.ImageUrl,
            conversation.Participants.Select(p => new ParticipantResponse(
                p.UserId, p.DisplayName, p.ProfileImage, p.Role, p.JoinedAt, p.LastReadMessageId
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
            conversation.UpdatedAt,
            unreadCount
        );
    }
}
