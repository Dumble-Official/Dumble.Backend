using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Enums;
using Xunit;

namespace Dumble.SharedKernel.Tests;

public class CurrentUserExtensionsTests
{
    private static CurrentUser With(UserType userType, params string[] roles) =>
        new("u1", "u@example.com", "U", null, userType, roles);

    [Fact]
    public void IsInRole_matches_userType_directly()
    {
        Assert.True(With(UserType.Admin).IsInRole(UserType.Admin));
        Assert.False(With(UserType.Participant).IsInRole(UserType.Admin));
    }

    [Theory]
    [InlineData("ROLE_ADMIN", UserType.Admin, true)]
    [InlineData("ADMIN", UserType.Admin, true)]
    [InlineData("admin", UserType.Admin, true)]
    [InlineData("ROLE_GYM_OWNER", UserType.GymOwner, true)]
    [InlineData("GymOwner", UserType.GymOwner, true)]
    [InlineData("ROLE_TRAINER", UserType.Admin, false)]
    public void IsInRole_matches_role_strings_in_either_form(string role, UserType target, bool expected)
    {
        Assert.Equal(expected, With(UserType.Participant, role).IsInRole(target));
    }

    [Fact]
    public void IsInAnyRole_returns_true_when_any_match()
    {
        var user = With(UserType.Trainer);
        Assert.True(user.IsInAnyRole(UserType.Admin, UserType.Trainer));
        Assert.False(user.IsInAnyRole(UserType.Admin, UserType.Moderator));
    }
}
