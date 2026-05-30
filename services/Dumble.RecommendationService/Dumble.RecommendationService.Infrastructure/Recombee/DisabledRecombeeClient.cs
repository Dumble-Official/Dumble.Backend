using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Domain.Outbox;
using Microsoft.Extensions.Logging;

namespace Dumble.RecommendationService.Infrastructure.Recombee;

/// <summary>
/// Stand-in used when Recombee is not configured (e.g. local dev without credentials). The
/// flush worker is not registered in that case, so this should never actually be invoked; it
/// exists so the DI graph resolves and the service still boots. Mirrors SocialService's
/// optional ranking-client pattern.
/// </summary>
public sealed class DisabledRecombeeClient : IRecombeeClient
{
    private readonly ILogger<DisabledRecombeeClient> _logger;

    public DisabledRecombeeClient(ILogger<DisabledRecombeeClient> logger) => _logger = logger;

    public Task SendInteractionsAsync(IReadOnlyList<OutboxInteraction> interactions, CancellationToken ct = default)
    {
        _logger.LogWarning(
            "Recombee is not configured; ignoring a request to send {Count} interactions", interactions.Count);
        return Task.CompletedTask;
    }

    public Task UpsertItemAsync(RecombeeItemUpsert item, CancellationToken ct = default) => Task.CompletedTask;

    public Task DeleteItemAsync(string itemId, CancellationToken ct = default) => Task.CompletedTask;

    public Task EnsureSchemaAsync(CancellationToken ct = default) => Task.CompletedTask;

    public Task<IReadOnlyList<string>> RecommendItemsToUserAsync(string userId, int count, CancellationToken ct = default)
        => Task.FromResult<IReadOnlyList<string>>(Array.Empty<string>());
}
