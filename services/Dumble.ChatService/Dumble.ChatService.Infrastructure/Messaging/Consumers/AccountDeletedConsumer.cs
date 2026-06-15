using Dumble.ChatService.Application.Contracts;
using Dumble.SharedKernel.Events.Accounts;
using MassTransit;
using Microsoft.Extensions.Logging;

namespace Dumble.ChatService.Infrastructure.Messaging.Consumers;

/// <summary>
/// Right-to-be-forgotten: on account deletion, remove the user from every conversation's
/// participant list and strip their identity (name + avatar) from the messages they authored.
/// Message bodies are kept so the conversation stays coherent for the remaining participants.
/// </summary>
public sealed class AccountDeletedConsumer : IConsumer<AccountDeletedEvent>
{
    private readonly IConversationRepository _conversations;
    private readonly IMessageRepository _messages;
    private readonly ILogger<AccountDeletedConsumer> _logger;

    public AccountDeletedConsumer(
        IConversationRepository conversations,
        IMessageRepository messages,
        ILogger<AccountDeletedConsumer> logger)
    {
        _conversations = conversations;
        _messages = messages;
        _logger = logger;
    }

    public async Task Consume(ConsumeContext<AccountDeletedEvent> context)
    {
        var userId = context.Message.UserId;
        if (string.IsNullOrWhiteSpace(userId))
        {
            _logger.LogWarning("Account-deleted event carried no userId; nothing to forget");
            return;
        }

        var ct = context.CancellationToken;
        await _conversations.RemoveParticipantEverywhereAsync(userId, ct);
        await _messages.AnonymizeSenderAsync(userId, ct);

        _logger.LogInformation("Forgot user {UserId}: removed from conversations and anonymized their messages", userId);
    }
}
