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

        await messageRepository.SoftDeleteAsync(request.MessageId, cancellationToken);

        await chatHubService.NotifyMessageDeletedAsync(
            message.ConversationId, request.MessageId, cancellationToken);
    }
}
