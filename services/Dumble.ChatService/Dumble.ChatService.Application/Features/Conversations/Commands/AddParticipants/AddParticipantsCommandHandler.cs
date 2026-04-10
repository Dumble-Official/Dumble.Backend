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
        var now = DateTime.UtcNow;

        foreach (var userId in request.UserIds)
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
