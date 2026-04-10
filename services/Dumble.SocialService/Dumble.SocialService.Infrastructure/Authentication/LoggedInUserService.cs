using System.Net.Http.Headers;
using System.Net.Http.Json;
using Microsoft.AspNetCore.Http;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Enums;

namespace Dumble.SocialService.Infrastructure.Authentication;

public class LoggedInUserService : ILoggedInUserService
{
    private readonly IHttpContextAccessor _httpContextAccessor;
    private readonly HttpClient _authHttpClient;

    public LoggedInUserService(IHttpContextAccessor httpContextAccessor, HttpClient authHttpClient)
    {
        _httpContextAccessor = httpContextAccessor;
        _authHttpClient = authHttpClient;
    }

    public async Task<CurrentUser> GetCurrentUserAsync(CancellationToken cancellationToken = default)
    {
        var authHeader = _httpContextAccessor.HttpContext?.Request.Headers["Authorization"].ToString();
        if (string.IsNullOrEmpty(authHeader)) throw new UnauthorizedAccessException();

        _authHttpClient.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue("Bearer", authHeader.Replace("Bearer ", ""));

        var response = await _authHttpClient.GetAsync("/api/users/me");
        response.EnsureSuccessStatusCode();

        var user = await response.Content.ReadFromJsonAsync<AuthUserResponse>();
        return new CurrentUser(
            Id: user!.Id.ToString(),
            Email: user.Email,
            DisplayName: user.DisplayName ?? $"{user.FirstName} {user.LastName}",
            ProfileImage: user.Pfp,
            UserType: Enum.Parse<UserType>(user.UserType, true)
        );
    }

    private record AuthUserResponse(
        long Id, string Email, string? DisplayName,
        string FirstName, string LastName, string? Pfp, string UserType);
}
