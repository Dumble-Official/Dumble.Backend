using System.Security.Claims;
using System.Text.Json;
using Dumble.SharedKernel.Authentication;
using Microsoft.AspNetCore.Authentication;

namespace Dumble.BundleManagementService.API.Authentication;

/// <summary>
/// Promotes the JWT's role information to <see cref="ClaimTypes.Role"/> claims so
/// FastEndpoints' <c>Roles(...)</c> gate (which calls <c>ClaimsPrincipal.IsInRole</c>)
/// works. Robust to both shapes the "roles" claim can take under
/// <c>MapInboundClaims = false</c> — individual claims OR a single JSON-array
/// string — and falls back to the single "userType" claim so a trainer/gym/admin
/// is authorised even when the roles array is absent or malformed.
/// </summary>
internal sealed class RolesClaimsTransformation : IClaimsTransformation
{
    public Task<ClaimsPrincipal> TransformAsync(ClaimsPrincipal principal)
    {
        if (principal.Identity is not ClaimsIdentity identity || !identity.IsAuthenticated)
            return Task.FromResult(principal);

        if (identity.HasClaim(c => c.Type == ClaimTypes.Role))
            return Task.FromResult(principal);

        void AddRole(string? value)
        {
            if (string.IsNullOrWhiteSpace(value)) return;
            // Strip the "ROLE_" prefix (case-insensitive) so "ROLE_ADMIN" lands as
            // "ADMIN", matching the UserType-name values endpoints declare.
            var name = value.StartsWith(AuthConstants.RolePrefix, StringComparison.OrdinalIgnoreCase)
                ? value[AuthConstants.RolePrefix.Length..]
                : value;
            name = name.Trim();
            if (name.Length == 0) return;
            if (!identity.HasClaim(ClaimTypes.Role, name))
                identity.AddClaim(new Claim(ClaimTypes.Role, name));
        }

        foreach (var raw in identity.FindAll(AuthConstants.RolesClaim).ToList())
        {
            var v = raw.Value?.Trim();
            // A single claim carrying a JSON array, e.g. ["ROLE_TRAINER"].
            if (v is { Length: > 0 } && v[0] == '[')
            {
                try
                {
                    foreach (var el in JsonSerializer.Deserialize<string[]>(v) ?? Array.Empty<string>())
                        AddRole(el);
                    continue;
                }
                catch (JsonException)
                {
                    // Not valid JSON after all — fall through and treat as a plain value.
                }
            }
            AddRole(raw.Value);
        }

        // Fallback: the user's single account type (always present as one claim).
        AddRole(identity.FindFirst("userType")?.Value);

        return Task.FromResult(principal);
    }
}
