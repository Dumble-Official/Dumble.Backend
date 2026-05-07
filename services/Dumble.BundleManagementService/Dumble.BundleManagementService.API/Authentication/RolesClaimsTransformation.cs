using System.Security.Claims;
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

        foreach (var raw in identity.FindAll("roles"))
        {
            var name = raw.Value.StartsWith("ROLE_", StringComparison.Ordinal)
                ? raw.Value[5..]
                : raw.Value;
            identity.AddClaim(new Claim(ClaimTypes.Role, name));
        }

        return Task.FromResult(principal);
    }
}
