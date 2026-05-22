using Dumble.ChatService.Application.Contracts;
using MediatR;

namespace Dumble.ChatService.Application.Features.Messages.Commands.EditMessage;

public class EditMessageCommandHandler(
    IMessageRepository messageRepository,
    IChatHubService chatHubService
) : IRequestHandler<EditMessageCommand>
{
    public async Task Handle(EditMessageCommand request, CancellationToken cancellationToken)
    {
        var message = await messageRepository.GetByIdAsync(request.MessageId, cancellationToken)
            ?? throw new KeyNotFoundException($"Message '{request.MessageId}' not found.");

        if (message.SenderId != request.CallerId)
            throw new UnauthorizedAccessException("You can only edit your own messages");

        if (message.IsDeleted)
            throw new InvalidOperationException("Cannot edit a deleted message");

        await messageRepository.EditAsync(request.MessageId, request.NewContent, cancellationToken);

        await chatHubService.NotifyMessageEditedAsync(
            message.ConversationId, request.MessageId, request.NewContent, cancellationToken);
    }
}
