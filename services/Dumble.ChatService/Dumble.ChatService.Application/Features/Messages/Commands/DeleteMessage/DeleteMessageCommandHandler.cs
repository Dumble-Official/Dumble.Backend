using Dumble.ChatService.Application.Contracts;
using MediatR;

namespace Dumble.ChatService.Application.Features.Messages.Commands.DeleteMessage;

public class DeleteMessageCommandHandler(
    IMessageRepository messageRepository,
    IChatHubService chatHubService
) : IRequestHandler<DeleteMessageCommand>
{
    public async Task Handle(DeleteMessageCommand request, CancellationToken cancellationToken)
    {
        var message = await messageRepository.GetByIdAsync(request.MessageId, cancellationToken)
            ?? throw new KeyNotFoundException($"Message '{request.MessageId}' not found.");

        if (message.SenderId != request.CallerId)
            throw new UnauthorizedAccessException("You can only delete your own messages");

        await messageRepository.SoftDeleteAsync(request.MessageId, cancellationToken);

        await chatHubService.NotifyMessageDeletedAsync(
            message.ConversationId, request.MessageId, cancellationToken);
    }
}
