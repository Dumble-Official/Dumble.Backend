using Dumble.ChatService.Application.Contracts;
using MediatR;

namespace Dumble.ChatService.Application.Features.Messages.Commands.EditMessage;

public class EditMessageCommandHandler(
    IMessageRepository messageRepository,
    IChatHubService chatHubService
) : IRequestHandler<EditMessageCommand>
{
    private const int MaxContentLength = 10000;
    private static readonly TimeSpan EditWindow = TimeSpan.FromHours(24);

    public async Task Handle(EditMessageCommand request, CancellationToken cancellationToken)
    {
        var message = await messageRepository.GetByIdAsync(request.MessageId, cancellationToken)
            ?? throw new KeyNotFoundException($"Message '{request.MessageId}' not found.");

        if (message.SenderId != request.CallerId)
            throw new UnauthorizedAccessException("You can only edit your own messages");

        if (message.IsDeleted)
            throw new InvalidOperationException("Cannot edit a deleted message");

        if (string.IsNullOrWhiteSpace(request.NewContent))
            throw new ArgumentException("Edited content cannot be empty");

        if (request.NewContent.Length > MaxContentLength)
            throw new ArgumentException($"Edited content exceeds maximum length of {MaxContentLength}");

        if (DateTime.UtcNow - message.CreatedAt > EditWindow)
            throw new InvalidOperationException("Messages can only be edited within 24 hours of sending");

        await messageRepository.EditAsync(request.MessageId, request.NewContent, cancellationToken);

        await chatHubService.NotifyMessageEditedAsync(
            message.ConversationId, request.MessageId, request.NewContent, cancellationToken);
    }
}
