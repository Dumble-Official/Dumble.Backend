using Dumble.ChatService.Application.Contracts;
using MediatR;

namespace Dumble.ChatService.Application.Features.Conversations.Commands.LeaveConversation;

public class LeaveConversationCommandHandler(
    IConversationRepository conversationRepository
) : IRequestHandler<LeaveConversationCommand>
{
    public async Task Handle(LeaveConversationCommand request, CancellationToken cancellationToken)
    {
        await conversationRepository.RemoveParticipantAsync(
            request.ConversationId, request.UserId, cancellationToken);
    }
}
