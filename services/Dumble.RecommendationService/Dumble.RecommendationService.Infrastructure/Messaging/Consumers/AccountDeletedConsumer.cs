using Dumble.RecommendationService.Application.Accounts;
using Dumble.SharedKernel.Events.Accounts;
using MassTransit;

namespace Dumble.RecommendationService.Infrastructure.Messaging.Consumers;

/// <summary>
/// Right-to-be-forgotten: when Authentication reports an account deletion, erase everything the
/// service holds about that user (Recombee profile + interactions, local projections).
/// </summary>
public sealed class AccountDeletedConsumer : IConsumer<AccountDeletedEvent>
{
    private readonly AccountForgetter _forgetter;

    public AccountDeletedConsumer(AccountForgetter forgetter) => _forgetter = forgetter;

    public Task Consume(ConsumeContext<AccountDeletedEvent> context)
        => _forgetter.ForgetAsync(context.Message.UserId, context.CancellationToken);
}
