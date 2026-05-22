using System.Security.Claims;
using Dumble.SharedKernel.Authentication;
using Microsoft.AspNetCore.Authentication;

namespace Dumble.BundleManagementService.API.Authentication;

internal sealed class RolesClaimsTransformation : IClaimsTransformation
{
    public Task<ClaimsPrincipal> TransformAsync(ClaimsPrincipal principal)
    {
        if (principal.Identity is not ClaimsIdentity identity || !identity.IsAuthenticated)
            return Task.FromResult(principal);

        if (identity.HasClaim(c => c.Type == ClaimTypes.Role))
            return Task.FromResult(principal);

        foreach (var raw in identity.FindAll(AuthConstants.RolesClaim).ToList())
        {
            // Match the case-insensitive strip used by CurrentUserExtensions so
            // a token carrying "role_admin" lands as the same ClaimTypes.Role
            // value as "ROLE_ADMIN".
            var name = raw.Value.StartsWith(AuthConstants.RolePrefix, StringComparison.OrdinalIgnoreCase)
                ? raw.Value[AuthConstants.RolePrefix.Length..]
                : raw.Value;
            identity.AddClaim(new Claim(ClaimTypes.Role, name));
        }

        return Task.FromResult(principal);
    }
}
