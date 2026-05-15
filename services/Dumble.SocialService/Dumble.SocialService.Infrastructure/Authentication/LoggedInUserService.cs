using System.Security.Claims;
using Microsoft.AspNetCore.Http;
using Dumble.SharedKernel.Authentication;
using Dumble.SharedKernel.Contracts;

namespace Dumble.SocialService.Infrastructure.Authentication;

public sealed class LoggedInUserService : ILoggedInUserService
{
    private readonly IHttpContextAccessor _httpContextAccessor;

    public LoggedInUserService(IHttpContextAccessor httpContextAccessor)
    {
        _httpContextAccessor = httpContextAccessor;
    }

    public CurrentUser GetCurrentUser()
    {
        var principal = _httpContextAccessor.HttpContext?.User
            ?? throw new UnauthorizedAccessException("No HttpContext.User available");

        var userId = principal.FindFirst("userId")?.Value
            ?? throw new UnauthorizedAccessException("Missing 'userId' claim");

        var email = principal.FindFirst(ClaimTypes.Email)?.Value
            ?? principal.FindFirst("sub")?.Value
            ?? principal.FindFirst(ClaimTypes.Name)?.Value
            ?? string.Empty;

        var displayName = principal.FindFirst("displayName")?.Value ?? email;
        var profileImage = principal.FindFirst("profileImage")?.Value;
        var userType = JwtUserTypeParser.Parse(principal.FindFirst("userType")?.Value);

        var roles = principal.FindAll(ClaimTypes.Role).Select(r => r.Value).ToList();
        if (roles.Count == 0)
            roles = principal.FindAll("roles").Select(r => r.Value).ToList();

        return new CurrentUser(userId, email, displayName, profileImage, userType, roles);
    }
}
