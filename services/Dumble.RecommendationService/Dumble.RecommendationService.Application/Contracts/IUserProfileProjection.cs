namespace Dumble.RecommendationService.Application.Contracts;

/// <summary>
/// A local projection of user display name + avatar, harvested from the actor fields on
/// events the service already consumes (design D7). Used to hydrate suggestion cards without
/// calling Authentication; users not present are simply omitted from suggestions.
/// </summary>
public interface IUserProfileProjection
{
    Task SetAsync(string userId, string displayName, string? profileImage, CancellationToken ct = default);
    Task<IReadOnlyDictionary<string, UserProfile>> GetManyAsync(IReadOnlyCollection<string> userIds, CancellationToken ct = default);
}
