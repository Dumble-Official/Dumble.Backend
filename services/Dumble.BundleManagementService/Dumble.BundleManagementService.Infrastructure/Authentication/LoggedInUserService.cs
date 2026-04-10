using System.Security.Claims;
using Dumble.BundleManagementService.Application.Contracts;
using Dumble.BundleManagementService.Domain.BundleAggregate.Enums;
using Microsoft.AspNetCore.Http;

namespace Dumble.BundleManagementService.Infrastructure.Authentication;

internal sealed class LoggedInUserService(IHttpContextAccessor httpContextAccessor) : ILoggedInUserService
{
    private readonly ClaimsPrincipal _claimsPrincipal = httpContextAccessor.HttpContext.User;
    
    public User GetCurrentUser()
    {
        // var user = new User()
        // {
        //     Id = Guid.Parse(_claimsPrincipal.FindFirst(ClaimTypes.NameIdentifier)!.Value),
        //     Email = _claimsPrincipal.FindFirst(ClaimTypes.Email)!.Value,
        //     AccountType = Enum.Parse<OwnerType>(_claimsPrincipal.FindFirst("AccountType")!.Value),
        //     Roles = _claimsPrincipal.FindAll(ClaimTypes.Role).Select(r => r.Value).ToList()
        // };

        var user = new User()
        {
            Id = Guid.Parse("11111111-1111-1111-1111-111111111111"),
            Email = "test@example.com",
            AccountType = OwnerType.Gym,
            Roles = new List<string> { "Admin", "Manager" }
        };

        
        return user;
    }
}