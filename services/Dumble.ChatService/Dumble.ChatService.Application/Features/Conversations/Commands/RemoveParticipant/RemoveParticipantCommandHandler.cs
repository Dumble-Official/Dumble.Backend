using Dumble.ChatService.Application.Contracts;
using MediatR;

namespace Dumble.ChatService.Application.Features.Conversations.Commands.RemoveParticipant;

public class RemoveParticipantCommandHandler(
    IConversationRepository conversationRepository
) : IRequestHandler<RemoveParticipantCommand>
{
    public async Task Handle(RemoveParticipantCommand request, CancellationToken cancellationToken)
    {
        var conversation = await conversationRepository.GetByIdAsync(request.ConversationId, cancellationToken)
            ?? throw new KeyNotFoundException($"Conversation '{request.ConversationId}' not found.");

        var caller = conversation.Participants.FirstOrDefault(p => p.UserId == request.CallerId);
        if (caller is null)
            throw new UnauthorizedAccessException("You are not a participant in this conversation");
        if (caller.Role != "Admin" && request.CallerId != request.TargetUserId)
            throw new UnauthorizedAccessException("Only admins can remove other participants");

        await conversationRepository.RemoveParticipantAsync(
            request.ConversationId, request.TargetUserId, cancellationToken);
    }
}
