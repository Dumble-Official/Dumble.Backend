using Dumble.ChatService.Application.Contracts;
using Dumble.ChatService.Domain.Models;
using MediatR;

namespace Dumble.ChatService.Application.Features.Conversations.Commands.AddParticipants;

public class AddParticipantsCommandHandler(
    IConversationRepository conversationRepository
) : IRequestHandler<AddParticipantsCommand>
{
    public async Task Handle(AddParticipantsCommand request, CancellationToken cancellationToken)
    {
        var conversation = await conversationRepository.GetByIdAsync(request.ConversationId, cancellationToken)
            ?? throw new KeyNotFoundException($"Conversation '{request.ConversationId}' not found.");

        var caller = conversation.Participants.FirstOrDefault(p => p.UserId == request.CallerId);
        if (caller is null)
            throw new UnauthorizedAccessException("You are not a participant in this conversation");
        if (caller.Role != "Admin")
            throw new UnauthorizedAccessException("Only admins can add participants");

        var existingIds = conversation.Participants.Select(p => p.UserId).ToHashSet();
        var now = DateTime.UtcNow;

        foreach (var userId in request.UserIds.Where(id => !existingIds.Contains(id)))
        {
            var participant = new Participant
            {
                UserId = userId,
                DisplayName = userId,
                Role = "Member",
                JoinedAt = now
            };

            await conversationRepository.AddParticipantAsync(
                request.ConversationId, participant, cancellationToken);
        }
    }
}
