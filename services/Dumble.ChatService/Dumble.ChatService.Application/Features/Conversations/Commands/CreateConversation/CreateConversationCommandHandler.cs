using Dumble.ChatService.Application.Contracts;
using Dumble.ChatService.Contracts.Conversations;
using Dumble.ChatService.Domain.Models;
using MediatR;

namespace Dumble.ChatService.Application.Features.Conversations.Commands.CreateConversation;

public class CreateConversationCommandHandler(
    IConversationRepository conversationRepository
) : IRequestHandler<CreateConversationCommand, ConversationResponse>
{
    public async Task<ConversationResponse> Handle(CreateConversationCommand request, CancellationToken cancellationToken)
    {
        if (request.Type == "Direct")
        {
            if (request.ParticipantIds.Count != 1)
                throw new InvalidOperationException("Direct conversations must have exactly one other participant.");

            var existing = await conversationRepository.GetDirectConversationAsync(
                request.CreatorId, request.ParticipantIds[0], cancellationToken);

            if (existing is not null)
                return MapToResponse(existing);
        }

        var now = DateTime.UtcNow;

        var participants = new List<Participant>
        {
            new()
            {
                UserId = request.CreatorId,
                DisplayName = request.CreatorName,
                ProfileImage = request.CreatorProfileImage,
                Role = "Admin",
                JoinedAt = now
            }
        };

        foreach (var userId in request.ParticipantIds)
        {
            participants.Add(new Participant
            {
                UserId = userId,
                DisplayName = userId,
                Role = "Member",
                JoinedAt = now
            });
        }

        var conversation = new Conversation
        {
            Type = request.Type,
            Name = request.Name,
            CreatedBy = request.CreatorId,
            Participants = participants,
            CreatedAt = now,
            UpdatedAt = now
        };

        await conversationRepository.CreateAsync(conversation, cancellationToken);

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
