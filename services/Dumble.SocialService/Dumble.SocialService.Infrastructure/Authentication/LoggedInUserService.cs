using System.Security.Claims;
using Microsoft.AspNetCore.Http;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Enums;

namespace Dumble.SocialService.Infrastructure.Authentication;

/// <summary>
/// Reads the current user out of the validated JWT claims already on
/// HttpContext.User — no extra HTTP call to Auth per request. Auth's JwtService
/// puts userId / roles / displayName / profileImage / userType in the token.
/// </summary>
public sealed class LoggedInUserService : ILoggedInUserService
{
    private readonly IHttpContextAccessor _httpContextAccessor;

    public LoggedInUserService(IHttpContextAccessor httpContextAccessor)
    {
        _httpContextAccessor = httpContextAccessor;
    }

    public Task<CurrentUser> GetCurrentUserAsync(CancellationToken cancellationToken = default)
    {
        var principal = _httpContextAccessor.HttpContext?.User
            ?? throw new UnauthorizedAccessException("No HttpContext.User available");

        var userId = principal.FindFirst("userId")?.Value
            ?? throw new UnauthorizedAccessException("Missing 'userId' claim");

        var email = principal.FindFirst(ClaimTypes.Email)?.Value
            ?? principal.FindFirst("sub")?.Value
            ?? principal.FindFirst(ClaimTypes.Name)?.Value
            ?? "";

        var displayName = principal.FindFirst("displayName")?.Value ?? email;
        var profileImage = principal.FindFirst("profileImage")?.Value;

        // Auth (Java) ships userType as the enum constant name in UPPER_SNAKE
        // (e.g. "GYM_OWNER"); the .NET enum is PascalCase ("GymOwner"). Strip
        // underscores so case-insensitive parse matches both shapes.
        var userTypeStr = principal.FindFirst("userType")?.Value ?? nameof(UserType.Participant);
        var normalised = userTypeStr.Replace("_", "");
        var userType = Enum.TryParse<UserType>(normalised, true, out var parsed) ? parsed : UserType.Participant;

        return Task.FromResult(new CurrentUser(
            Id: userId,
            Email: email,
            DisplayName: displayName,
            ProfileImage: profileImage,
            UserType: userType
        ));
    }
}
