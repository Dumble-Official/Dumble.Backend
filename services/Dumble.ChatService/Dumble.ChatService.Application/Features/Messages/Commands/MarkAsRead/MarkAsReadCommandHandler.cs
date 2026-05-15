using Dumble.ChatService.Application.Contracts;
using MediatR;

namespace Dumble.ChatService.Application.Features.Messages.Commands.MarkAsRead;

public class MarkAsReadCommandHandler(
    IConversationRepository conversationRepository
) : IRequestHandler<MarkAsReadCommand>
{
    public async Task Handle(MarkAsReadCommand request, CancellationToken cancellationToken)
    {
        var conversation = await conversationRepository.GetByIdAsync(request.ConversationId, cancellationToken)
            ?? throw new KeyNotFoundException($"Conversation '{request.ConversationId}' not found.");

        if (!conversation.Participants.Any(p => p.UserId == request.UserId))
            throw new UnauthorizedAccessException("You are not a participant in this conversation");

        await conversationRepository.UpdateLastReadAsync(
            request.ConversationId, request.UserId, request.MessageId, cancellationToken);
    }
}
