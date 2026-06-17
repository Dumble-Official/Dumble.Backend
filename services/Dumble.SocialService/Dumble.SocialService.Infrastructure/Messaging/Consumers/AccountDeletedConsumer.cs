using Dumble.SocialService.Application.Contracts;
using Dumble.SharedKernel.Events.Accounts;
using MassTransit;
using Microsoft.Extensions.Logging;

namespace Dumble.SocialService.Infrastructure.Messaging.Consumers;

/// <summary>
/// Right-to-be-forgotten: when an account is deleted, erase the user from the social graph —
/// every follow edge touching them (in both directions, so no one keeps a ghost follower).
/// SocialService is the source of truth for follows, so this is the authoritative cleanup.
/// </summary>
public sealed class AccountDeletedConsumer : IConsumer<AccountDeletedEvent>
{
    private readonly IFollowRepository _follows;
    private readonly ILogger<AccountDeletedConsumer> _logger;

    public AccountDeletedConsumer(
        IFollowRepository follows,
        ILogger<AccountDeletedConsumer> logger)
    {
        _follows = follows;
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

        var edges = await _follows.DeleteAllForUserAsync(userId, context.CancellationToken);
        _logger.LogInformation("Forgot user {UserId}: removed {Edges} follow edges", userId, edges);
    }
}
