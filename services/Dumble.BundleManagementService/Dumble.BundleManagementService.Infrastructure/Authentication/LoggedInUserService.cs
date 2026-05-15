using System.Security.Claims;
using Dumble.BundleManagementService.Application.Contracts;
using Dumble.BundleManagementService.Domain.BundleAggregate.Enums;
using Microsoft.AspNetCore.Http;

namespace Dumble.BundleManagementService.Infrastructure.Authentication;

internal sealed class LoggedInUserService(IHttpContextAccessor httpContextAccessor) : ILoggedInUserService
{
    private readonly ClaimsPrincipal _claimsPrincipal = httpContextAccessor.HttpContext!.User;
    
    public User GetCurrentUser()
    {
        // Auth JWT carries: sub (email), userId (long), roles (list of "ROLE_*"),
        // displayName, profileImage, userType. With MapInboundClaims=false the
        // raw claim names are preserved on HttpContext.User.
        var userIdClaim = _claimsPrincipal.FindFirst("userId")?.Value
            ?? throw new UnauthorizedAccessException("Missing userId claim");

        var email = _claimsPrincipal.FindFirst(ClaimTypes.Name)?.Value
            ?? _claimsPrincipal.FindFirst("sub")?.Value
            ?? "";

        var roles = _claimsPrincipal.FindAll(ClaimTypes.Role)
            .Select(r => r.Value)
            .ToList();

        // If no standard role claims, try the "roles" claim from the JWT
        if (roles.Count == 0)
        {
            roles = _claimsPrincipal.FindAll("roles")
                .Select(r => r.Value)
                .ToList();
        }

        // Convert long userId to a deterministic GUID for domain compatibility
        var userId = long.Parse(userIdClaim);
        var guidBytes = new byte[16];
        BitConverter.GetBytes(userId).CopyTo(guidBytes, 0);

        // OwnerType.Gym means "this bundle is owned by the gym side of the
        // marketplace" — i.e. the human who owns the gym (GYM_OWNER), not the
        // gym page itself (GYM). Trainers and participants get OwnerType.User.
        var accountType = roles.Any(r => r.Equals("ROLE_GYM_OWNER", StringComparison.OrdinalIgnoreCase))
            ? OwnerType.Gym
            : OwnerType.User;

        return new User
        {
            Id = new Guid(guidBytes),
            Email = email,
            AccountType = accountType,
            Roles = roles
        };
    }
}