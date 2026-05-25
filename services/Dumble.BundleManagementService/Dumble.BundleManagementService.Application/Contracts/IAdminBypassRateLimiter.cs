namespace Dumble.BundleManagementService.Application.Contracts;

public interface IAdminBypassRateLimiter
{
    bool IsAllowed(string adminId, string actionType);
}
