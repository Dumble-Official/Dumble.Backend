using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Contracts.Common;
using Dumble.RecommendationService.Contracts.Suggestions;
using MediatR;
using Microsoft.Extensions.Logging;

namespace Dumble.RecommendationService.Application.Features.Suggestions.GetSuggestedUsers;

/// <summary>
/// Recommend users to follow: ask Recombee for similar users (by interaction graph), over-fetch
/// to absorb filtering, drop the caller and anyone they already follow, take the page, and
/// hydrate names/avatars from the local profile projection — omitting any user we have no
/// profile for. Degrades to empty when Recombee is unavailable (recency is not meaningful for
/// users), per design D5/D7/D8.
/// </summary>
public sealed class GetSuggestedUsersQueryHandler
    : IRequestHandler<GetSuggestedUsersQuery, CursorPagedResponse<SuggestedUserResponse>>
{
    private const int OverFetchFactor = 3;
    private const int MaxCandidates = 100;

    private readonly IRecombeeClient _recombee;
    private readonly IFollowProjection _follows;
    private readonly IUserProfileProjection _profiles;
    private readonly IBannedUserStore _bannedUsers;
    private readonly ILogger<GetSuggestedUsersQueryHandler> _logger;

    public GetSuggestedUsersQueryHandler(
        IRecombeeClient recombee,
        IFollowProjection follows,
        IUserProfileProjection profiles,
        IBannedUserStore bannedUsers,
        ILogger<GetSuggestedUsersQueryHandler> logger)
    {
        _recombee = recombee;
        _follows = follows;
        _profiles = profiles;
        _bannedUsers = bannedUsers;
        _logger = logger;
    }

    public async Task<CursorPagedResponse<SuggestedUserResponse>> Handle(
        GetSuggestedUsersQuery request, CancellationToken ct)
    {
        var fetch = Math.Min(request.Limit * OverFetchFactor, MaxCandidates);

        IReadOnlyList<string> candidates;
        try
        {
            candidates = await _recombee.RecommendUsersToUserAsync(request.UserId, fetch, ct);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Recombee user recommendation failed for {UserId}", request.UserId);
            return Empty();
        }

        if (candidates.Count == 0)
            return Empty();

        var followees = await _follows.GetFolloweesAsync(request.UserId, ct);
        var eligible = candidates
            .Where(id => id != request.UserId && !followees.Contains(id))
            .ToList();

        // Never suggest a banned account — tapping Follow would hit a blocked user.
        var banned = await _bannedUsers.GetBannedAsync(eligible, ct);
        var shortlist = eligible
            .Where(id => !banned.Contains(id))
            .Take(request.Limit)
            .ToList();

        if (shortlist.Count == 0)
            return Empty();

        // Hydrate from the local projection; omit anyone we have no profile for (no blank cards).
        var profiles = await _profiles.GetManyAsync(shortlist, ct);
        var users = shortlist
            .Where(profiles.ContainsKey)
            .Select(id => new SuggestedUserResponse(id, profiles[id].DisplayName, profiles[id].ProfileImage))
            .ToList();

        // No pagination cursor in v1 — suggestions are a short, refreshed list.
        return new CursorPagedResponse<SuggestedUserResponse>(users, null, false);
    }

    private static CursorPagedResponse<SuggestedUserResponse> Empty()
        => new(new List<SuggestedUserResponse>(), null, false);
}
