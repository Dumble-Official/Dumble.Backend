using Dumble.SharedKernel.Authentication;
using Dumble.SharedKernel.Enums;

namespace Dumble.SharedKernel.Contracts;

public static class CurrentUserExtensions
{
    public static bool IsInRole(this CurrentUser user, UserType role)
    {
        if (user.UserType == role) return true;
        if (user.Roles is null) return false;

        var roleName = role.ToString().ToUpperInvariant();
        var snakeName = ToUpperSnake(role.ToString());

        foreach (var raw in user.Roles)
        {
            if (string.IsNullOrWhiteSpace(raw)) continue;
            var stripped = raw.StartsWith(AuthConstants.RolePrefix, StringComparison.OrdinalIgnoreCase)
                ? raw[AuthConstants.RolePrefix.Length..]
                : raw;
            if (stripped.Equals(roleName, StringComparison.OrdinalIgnoreCase)) return true;
            if (stripped.Equals(snakeName, StringComparison.OrdinalIgnoreCase)) return true;
        }

        return false;
    }

    public static bool IsInAnyRole(this CurrentUser user, params UserType[] roles)
    {
        foreach (var r in roles)
            if (user.IsInRole(r)) return true;
        return false;
    }

    private static string ToUpperSnake(string pascal)
    {
        if (string.IsNullOrEmpty(pascal)) return pascal;
        var sb = new System.Text.StringBuilder(pascal.Length + 4);
        for (var i = 0; i < pascal.Length; i++)
        {
            var c = pascal[i];
            if (i > 0 && char.IsUpper(c)) sb.Append('_');
            sb.Append(char.ToUpperInvariant(c));
        }
        return sb.ToString();
    }
}
