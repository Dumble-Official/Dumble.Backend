namespace Dumble.RecommendationService.Application.Contracts;

/// <summary>
/// Reads the platform's banned-user set (the same Redis set the Auth service maintains and the
/// gateway enforces), so a banned user is never offered as a follow suggestion.
/// </summary>
public interface IBannedUserStore
{
    /// <summary>Of the given ids, returns those that are currently banned.</summary>
    Task<IReadOnlySet<string>> GetBannedAsync(IReadOnlyCollection<string> userIds, CancellationToken ct = default);
}
