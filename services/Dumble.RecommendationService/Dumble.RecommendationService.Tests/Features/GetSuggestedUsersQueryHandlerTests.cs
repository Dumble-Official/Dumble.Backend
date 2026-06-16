using Dumble.RecommendationService.Application.Features.Suggestions.GetSuggestedUsers;
using Dumble.RecommendationService.Tests.TestDoubles;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace Dumble.RecommendationService.Tests.Features;

public class GetSuggestedUsersQueryHandlerTests
{
    private readonly FakeRecombeeClient _recombee = new();
    private readonly FakeFollowProjection _follows = new();
    private readonly FakeUserProfileProjection _profiles = new();
    private readonly FakeBannedUserStore _banned = new();

    private GetSuggestedUsersQueryHandler Handler() =>
        new(_recombee, _follows, _profiles, _banned, NullLogger<GetSuggestedUsersQueryHandler>.Instance);

    [Fact]
    public async Task Excludes_banned_users()
    {
        _recombee.RecommendUsersResult = new[] { "u2", "u3" };
        _profiles.Seed("u2", "User Two");
        _profiles.Seed("u3", "User Three");
        _banned.Banned.Add("u2");

        var result = await Handler().Handle(new GetSuggestedUsersQuery("me", 10), CancellationToken.None);

        Assert.Equal(new[] { "u3" }, result.Items.Select(u => u.UserId));
    }

    [Fact]
    public async Task Excludes_self_and_already_followed_users()
    {
        _recombee.RecommendUsersResult = new[] { "me", "f1", "u2", "u3" };
        _follows.Seed("me", "f1");
        _profiles.Seed("u2", "User Two");
        _profiles.Seed("u3", "User Three");

        var result = await Handler().Handle(new GetSuggestedUsersQuery("me", 10), CancellationToken.None);

        Assert.Equal(new[] { "u2", "u3" }, result.Items.Select(u => u.UserId));
    }

    [Fact]
    public async Task Omits_users_with_no_captured_profile()
    {
        _recombee.RecommendUsersResult = new[] { "u1", "u2", "u3" };
        _profiles.Seed("u1", "One");
        _profiles.Seed("u3", "Three"); // u2 has no profile

        var result = await Handler().Handle(new GetSuggestedUsersQuery("me", 10), CancellationToken.None);

        Assert.Equal(new[] { "u1", "u3" }, result.Items.Select(u => u.UserId));
    }

    [Fact]
    public async Task Hydrates_name_and_image()
    {
        _recombee.RecommendUsersResult = new[] { "u1" };
        _profiles.Seed("u1", "Coach Sam", "http://img/sam.png");

        var result = await Handler().Handle(new GetSuggestedUsersQuery("me", 10), CancellationToken.None);

        var u = Assert.Single(result.Items);
        Assert.Equal("Coach Sam", u.DisplayName);
        Assert.Equal("http://img/sam.png", u.ProfileImage);
    }

    [Fact]
    public async Task Returns_empty_when_recombee_returns_nothing()
    {
        _recombee.RecommendUsersResult = Array.Empty<string>();

        var result = await Handler().Handle(new GetSuggestedUsersQuery("me", 10), CancellationToken.None);

        Assert.Empty(result.Items);
    }

    [Fact]
    public async Task Returns_empty_when_recombee_throws()
    {
        _recombee.ThrowOnRecommendUsers = new InvalidOperationException("recombee down");

        var result = await Handler().Handle(new GetSuggestedUsersQuery("me", 10), CancellationToken.None);

        Assert.Empty(result.Items);
    }

    [Fact]
    public async Task Respects_the_limit_after_filtering()
    {
        _recombee.RecommendUsersResult = new[] { "u1", "u2", "u3", "u4", "u5", "u6" };
        foreach (var id in _recombee.RecommendUsersResult)
            _profiles.Seed(id, id.ToUpperInvariant());

        var result = await Handler().Handle(new GetSuggestedUsersQuery("me", 2), CancellationToken.None);

        Assert.Equal(new[] { "u1", "u2" }, result.Items.Select(u => u.UserId));
    }
}
