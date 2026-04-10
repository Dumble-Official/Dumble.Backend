using System.Net.Http.Headers;
using System.Net.Http.Json;
using Microsoft.AspNetCore.Http;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Enums;

namespace Dumble.PostService.Infrastructure.Authentication;

public class LoggedInUserService : ILoggedInUserService
{
    private readonly IHttpContextAccessor _httpContextAccessor;
    private readonly HttpClient _httpClient;

    public LoggedInUserService(IHttpContextAccessor httpContextAccessor, HttpClient httpClient)
    {
        _httpContextAccessor = httpContextAccessor;
        _httpClient = httpClient;
    }

    public async Task<CurrentUser> GetCurrentUserAsync(CancellationToken ct)
    {
        var authHeader = _httpContextAccessor.HttpContext?.Request.Headers["Authorization"].ToString();
        if (string.IsNullOrEmpty(authHeader))
            throw new UnauthorizedAccessException("No authorization header found");

        var token = authHeader.Replace("Bearer ", "", StringComparison.OrdinalIgnoreCase);
        _httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);

        var response = await _httpClient.GetAsync("/api/users/me", ct);
        response.EnsureSuccessStatusCode();

        var user = await response.Content.ReadFromJsonAsync<AuthUserResponse>(ct)
            ?? throw new UnauthorizedAccessException("Failed to retrieve user information");

        return new CurrentUser(
            Id: user.Id.ToString(),
            Email: user.Email,
            DisplayName: user.DisplayName ?? $"{user.FirstName} {user.LastName}".Trim(),
            ProfileImage: user.Pfp,
            UserType: Enum.Parse<UserType>(user.UserType, true)
        );
    }

    private record AuthUserResponse(
        long Id,
        string Email,
        string? DisplayName,
        string FirstName,
        string LastName,
        string? Pfp,
        string UserType
    );
}
