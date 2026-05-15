namespace Dumble.SharedKernel.Authentication;

/// <summary>
/// Single source of truth for JWT auth wire-format strings. Auth-side issuance,
/// gateway forwarding, per-service claims-transformation, and the
/// <see cref="Dumble.SharedKernel.Contracts.CurrentUserExtensions"/> helpers
/// must all agree on these — a typo on one side breaks the system silently
/// (e.g. issuing "Roles" vs reading "roles" leaves every user with no roles).
/// </summary>
public static class AuthConstants
{
    /// <summary>Name of the array-of-string claim that carries the user's role list in JWTs minted by the Auth service.</summary>
    public const string RolesClaim = "roles";

    /// <summary>
    /// Prefix the Auth service stamps on every role value (e.g. <c>ROLE_ADMIN</c>).
    /// Strip before comparing to <see cref="Dumble.SharedKernel.Enums.UserType"/>
    /// names. Comparison itself should be case-insensitive on the role body — but
    /// the prefix is always emitted in uppercase by Auth.
    /// </summary>
    public const string RolePrefix = "ROLE_";
}
