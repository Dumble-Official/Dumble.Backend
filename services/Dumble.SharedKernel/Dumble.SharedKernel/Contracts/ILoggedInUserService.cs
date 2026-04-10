namespace Dumble.SharedKernel.Contracts;

public interface ILoggedInUserService
{
    Task<CurrentUser> GetCurrentUserAsync(CancellationToken cancellationToken = default);
}
