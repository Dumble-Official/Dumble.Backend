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

    [Fact]
    public void IsInRole_returns_false_for_empty_roles_list_when_userType_does_not_match()
    {
        // Most-likely production state right after registration (UserType=Participant,
        // no admin/moderator elevation yet) — extension must report 'not in role'
        // rather than throw on the empty enumerable.
        var user = With(UserType.Participant);
        Assert.False(user.IsInRole(UserType.Admin));
        Assert.False(user.IsInAnyRole(UserType.Admin, UserType.Moderator));
    }

    [Theory]
    [InlineData("   ")]
    [InlineData("")]
    [InlineData("\t\n")]
    public void IsInRole_ignores_whitespace_only_role_strings(string blank)
    {
        var user = With(UserType.Participant, blank);
        Assert.False(user.IsInRole(UserType.Admin));
    }

    [Theory]
    [InlineData("role_admin", UserType.Admin, true)]      // lowercase prefix accepted (case-insensitive strip)
    [InlineData("Role_Admin", UserType.Admin, true)]      // mixed-case prefix
    [InlineData("random-string", UserType.Admin, false)]  // non-canonical, no match
    [InlineData("AdminBackup", UserType.Admin, false)]    // substring, no false-positive
    public void IsInRole_handles_non_canonical_inputs(string role, UserType target, bool expected)
    {
        Assert.Equal(expected, With(UserType.Participant, role).IsInRole(target));
    }
}
