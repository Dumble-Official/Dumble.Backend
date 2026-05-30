using Dumble.RecommendationService.Application.Contracts;
using Microsoft.Extensions.Logging;

namespace Dumble.RecommendationService.Application.Accounts;

/// <summary>
/// Right-to-be-forgotten: erase everything the recommendation service holds about a user when
/// their account is deleted. That is their Recombee profile + interactions (the model's training
/// data) and the local Redis projections keyed to them. Their posts are items owned by
/// PostService, so they are purged via the normal PostDeleted cascade, not here.
/// </summary>
public sealed class AccountForgetter
{
    private readonly IRecombeeClient _recombee;
    private readonly IUserProfileProjection _profiles;
    private readonly IFollowProjection _follows;
    private readonly ILogger<AccountForgetter> _logger;

    public AccountForgetter(
        IRecombeeClient recombee,
        IUserProfileProjection profiles,
        IFollowProjection follows,
        ILogger<AccountForgetter> logger)
    {
        _recombee = recombee;
        _profiles = profiles;
        _follows = follows;
        _logger = logger;
    }

    public async Task ForgetAsync(string userId, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(userId))
        {
            _logger.LogWarning("Account-deleted event carried no userId; nothing to forget");
            return;
        }

        await _recombee.DeleteUserAsync(userId, ct);
        await _profiles.RemoveAsync(userId, ct);
        await _follows.RemoveUserAsync(userId, ct);

        _logger.LogInformation("Forgot user {UserId} after account deletion", userId);
    }
}
