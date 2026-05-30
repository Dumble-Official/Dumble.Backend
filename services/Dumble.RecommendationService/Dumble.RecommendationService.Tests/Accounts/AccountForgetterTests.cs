using Dumble.RecommendationService.Application.Accounts;
using Dumble.RecommendationService.Tests.TestDoubles;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace Dumble.RecommendationService.Tests.Accounts;

public class AccountForgetterTests
{
    private static (AccountForgetter Forgetter, FakeRecombeeClient Recombee, FakeUserProfileProjection Profiles, FakeFollowProjection Follows) Build()
    {
        var recombee = new FakeRecombeeClient();
        var profiles = new FakeUserProfileProjection();
        var follows = new FakeFollowProjection();
        var forgetter = new AccountForgetter(recombee, profiles, follows, NullLogger<AccountForgetter>.Instance);
        return (forgetter, recombee, profiles, follows);
    }

    [Fact]
    public async Task Forgets_the_user_everywhere()
    {
        var (forgetter, recombee, profiles, follows) = Build();
        profiles.Seed("u1", "Alice", "img");
        follows.Seed("u1", "u2", "u3");

        await forgetter.ForgetAsync("u1");

        Assert.Equal(new[] { "u1" }, recombee.DeletedUsers);
        Assert.False(profiles.Contains("u1"));
        Assert.False(follows.Contains("u1"));
    }

    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    public async Task Ignores_a_missing_user_id(string userId)
    {
        var (forgetter, recombee, _, _) = Build();

        await forgetter.ForgetAsync(userId);

        // No destructive call made on a malformed event.
        Assert.Empty(recombee.DeletedUsers);
    }
}
