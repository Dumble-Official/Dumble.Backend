using Dumble.ChatService.Application.Contracts;
using Dumble.ChatService.Contracts.Conversations;
using Dumble.ChatService.Domain.Models;
using MediatR;

namespace Dumble.ChatService.Application.Features.Conversations.Queries.GetConversation;

public class GetConversationQueryHandler(
    IConversationRepository conversationRepository
) : IRequestHandler<GetConversationQuery, ConversationResponse>
{
    public async Task<ConversationResponse> Handle(GetConversationQuery request, CancellationToken cancellationToken)
    {
        var conversation = await conversationRepository.GetByIdAsync(request.ConversationId, cancellationToken)
            ?? throw new KeyNotFoundException($"Conversation '{request.ConversationId}' not found.");

        if (!conversation.Participants.Any(p => p.UserId == request.CallerId))
            throw new UnauthorizedAccessException("You are not a participant in this conversation");

        return MapToResponse(conversation);
    }

    private static ConversationResponse MapToResponse(Conversation conversation)
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
            conversation.UpdatedAt
        );
    }
}
