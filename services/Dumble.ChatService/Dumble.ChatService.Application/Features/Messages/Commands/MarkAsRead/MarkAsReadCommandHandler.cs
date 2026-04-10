using Dumble.ChatService.Application.Contracts;
using MediatR;

namespace Dumble.ChatService.Application.Features.Messages.Commands.MarkAsRead;

public class MarkAsReadCommandHandler(
    IConversationRepository conversationRepository
) : IRequestHandler<MarkAsReadCommand>
{
    public async Task Handle(MarkAsReadCommand request, CancellationToken cancellationToken)
    {
        await conversationRepository.UpdateLastReadAsync(
            request.ConversationId, request.UserId, request.MessageId, cancellationToken);
    }
}
