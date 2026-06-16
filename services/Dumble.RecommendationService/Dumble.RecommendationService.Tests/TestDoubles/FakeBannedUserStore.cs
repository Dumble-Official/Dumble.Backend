using Dumble.RecommendationService.Application.Contracts;

namespace Dumble.RecommendationService.Tests.TestDoubles;

internal sealed class FakeBannedUserStore : IBannedUserStore
{
    public HashSet<string> Banned { get; } = new();

    public Task<IReadOnlySet<string>> GetBannedAsync(IReadOnlyCollection<string> userIds, CancellationToken ct = default)
        => Task.FromResult<IReadOnlySet<string>>(userIds.Where(Banned.Contains).ToHashSet());
}
