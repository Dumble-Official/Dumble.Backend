using Dumble.SharedKernel.Authentication;
using Dumble.SharedKernel.Enums;
using Xunit;

namespace Dumble.SharedKernel.Tests;

public class JwtUserTypeParserTests
{
    [Theory]
    [InlineData("PARTICIPANT", UserType.Participant)]
    [InlineData("MODERATOR", UserType.Moderator)]
    [InlineData("TRAINER", UserType.Trainer)]
    [InlineData("GYM_OWNER", UserType.GymOwner)]
    [InlineData("GYM", UserType.Gym)]
    [InlineData("ADMIN", UserType.Admin)]
    public void Parses_java_upper_snake_form(string raw, UserType expected)
    {
        Assert.Equal(expected, JwtUserTypeParser.Parse(raw));
    }

    [Theory]
    [InlineData("Participant", UserType.Participant)]
    [InlineData("GymOwner", UserType.GymOwner)]
    [InlineData("gym", UserType.Gym)]
    public void Parses_pascal_and_lower_forms(string raw, UserType expected)
    {
        Assert.Equal(expected, JwtUserTypeParser.Parse(raw));
    }

    [Theory]
    [InlineData("")]
    [InlineData(null)]
    [InlineData("   ")]
    [InlineData("not-a-real-role")]
    public void Falls_back_to_participant_for_unknown_or_empty(string? raw)
    {
        Assert.Equal(UserType.Participant, JwtUserTypeParser.Parse(raw));
    }

    [Fact]
    public void Honors_explicit_fallback()
    {
        Assert.Equal(UserType.Admin, JwtUserTypeParser.Parse(null, fallback: UserType.Admin));
    }
}
