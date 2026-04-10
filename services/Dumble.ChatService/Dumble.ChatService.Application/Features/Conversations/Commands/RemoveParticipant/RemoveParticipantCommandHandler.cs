using Dumble.ChatService.Application.Contracts;
using MediatR;

namespace Dumble.ChatService.Application.Features.Conversations.Commands.RemoveParticipant;

public class RemoveParticipantCommandHandler(
    IConversationRepository conversationRepository
) : IRequestHandler<RemoveParticipantCommand>
{
    public async Task Handle(RemoveParticipantCommand request, CancellationToken cancellationToken)
    {
        await conversationRepository.RemoveParticipantAsync(
            request.ConversationId, request.UserId, cancellationToken);
    }
}
