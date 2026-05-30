using Dumble.RecommendationService.Application.Contracts;

namespace Dumble.RecommendationService.Tests.TestDoubles;

internal sealed class FakeUserProfileProjection : IUserProfileProjection
{
    private readonly Dictionary<string, UserProfile> _profiles = new();

    public void Seed(string userId, string displayName, string? image = null)
        => _profiles[userId] = new UserProfile(displayName, image);

    public Task SetAsync(string userId, string displayName, string? profileImage, CancellationToken ct = default)
    {
        _profiles[userId] = new UserProfile(displayName, profileImage);
        return Task.CompletedTask;
    }

    public Task<IReadOnlyDictionary<string, UserProfile>> GetManyAsync(
        IReadOnlyCollection<string> userIds, CancellationToken ct = default)
    {
        IReadOnlyDictionary<string, UserProfile> result = userIds
            .Where(_profiles.ContainsKey)
            .ToDictionary(id => id, id => _profiles[id]);
        return Task.FromResult(result);
    }
}
