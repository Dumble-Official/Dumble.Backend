using System.Security.Claims;
using System.Text.Json;
using Microsoft.AspNetCore.Authentication;

namespace Dumble.PostService.API.Authentication;

/// <summary>
/// Promotes the JWT's role info to <see cref="ClaimTypes.Role"/> so policy/role
/// checks work. Robust to both shapes the "roles" claim can take under
/// MapInboundClaims=false — individual claims OR a single JSON-array string —
/// and falls back to the single "userType" claim so admin/trainer/gym accounts
/// are recognised even when the roles array is absent or malformed.
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
            var name = value.StartsWith("ROLE_", StringComparison.OrdinalIgnoreCase)
                ? value[5..]
                : value;
            name = name.Trim();
            if (name.Length == 0) return;
            if (!identity.HasClaim(ClaimTypes.Role, name))
                identity.AddClaim(new Claim(ClaimTypes.Role, name));
        }

        foreach (var raw in identity.FindAll("roles").ToList())
        {
            var v = raw.Value?.Trim();
            if (v is { Length: > 0 } && v[0] == '[')
            {
                try
                {
                    foreach (var el in JsonSerializer.Deserialize<string[]>(v) ?? Array.Empty<string>())
                        AddRole(el);
                    continue;
                }
                catch (JsonException) { /* treat as a plain value below */ }
            }
            AddRole(raw.Value);
        }

        AddRole(identity.FindFirst("userType")?.Value);

        return Task.FromResult(principal);
    }
}
