using Dumble.SharedKernel.Enums;

namespace Dumble.SharedKernel.Authentication;

public static class JwtUserTypeParser
{
    public static UserType Parse(string? rawClaim, UserType fallback = UserType.Participant)
    {
        if (string.IsNullOrWhiteSpace(rawClaim))
            return fallback;

        var normalised = rawClaim.Replace("_", string.Empty);
        return Enum.TryParse<UserType>(normalised, ignoreCase: true, out var parsed)
            ? parsed
            : fallback;
    }
}
