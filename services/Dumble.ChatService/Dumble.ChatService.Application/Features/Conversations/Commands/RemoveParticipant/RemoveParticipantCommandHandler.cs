using Dumble.ChatService.Application.Contracts;
using MediatR;

namespace Dumble.ChatService.Application.Features.Conversations.Commands.RemoveParticipant;

public class RemoveParticipantCommandHandler(
    IConversationRepository conversationRepository,
    IChatHubService hubService
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

        // Notify the removed user's connected hub session so the client
        // unsubscribes from the conversation group. Without this, an
        // ex-participant with an existing WebSocket keeps receiving group
        // broadcasts (privacy leak in the kick-from-group case).
        await hubService.NotifyRemovedFromConversationAsync(
            request.TargetUserId, request.ConversationId, cancellationToken);
    }
}
