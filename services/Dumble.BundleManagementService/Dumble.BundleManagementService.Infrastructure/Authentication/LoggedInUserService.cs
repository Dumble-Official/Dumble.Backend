using System.Security.Claims;
using Dumble.BundleManagementService.Application.Contracts;
using Dumble.BundleManagementService.Domain.BundleAggregate.Enums;
using Microsoft.AspNetCore.Http;

namespace Dumble.BundleManagementService.Infrastructure.Authentication;

internal sealed class LoggedInUserService(IHttpContextAccessor httpContextAccessor) : ILoggedInUserService
{
    public User GetCurrentUser()
    {
        var httpContext = httpContextAccessor.HttpContext
            ?? throw new UnauthorizedAccessException("No HTTP context available");

        var principal = httpContext.User;

        var userIdStr = principal.FindFirst("userId")?.Value
            ?? throw new UnauthorizedAccessException("userId claim missing from token");

        var email = principal.FindFirst("email")?.Value
                 ?? principal.FindFirst("sub")?.Value
                 ?? "";

        // Map Auth service userType to BundleManagement OwnerType
        var userTypeStr = principal.FindFirst("userType")?.Value ?? "";
        var accountType = userTypeStr.ToUpperInvariant() switch
        {
            "TRAINER" => OwnerType.Trainer,
            "OWNER" => OwnerType.Gym,
            _ => OwnerType.Gym // default fallback
        };

        var roles = principal.FindAll("roles").Select(r => r.Value).ToList();

        // Auth service uses Long IDs; convert to deterministic Guid for domain compatibility
        Guid userId;
        if (!Guid.TryParse(userIdStr, out userId))
        {
            var longId = long.Parse(userIdStr);
            var bytes = new byte[16];
            BitConverter.GetBytes(longId).CopyTo(bytes, 0);
            userId = new Guid(bytes);
        }

        return new User
        {
            Id = userId,
            Email = email,
            AccountType = accountType,
            Roles = roles
        };
    }
}