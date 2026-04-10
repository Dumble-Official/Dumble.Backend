using Dumble.NotificationService.Domain.Models;

namespace Dumble.NotificationService.Application.Contracts;

public interface IDeviceTokenRepository
{
    Task<List<DeviceToken>> GetByUserIdAsync(string userId, CancellationToken ct = default);
    Task UpsertAsync(DeviceToken deviceToken, CancellationToken ct = default);
    Task DeleteByTokenAsync(string token, CancellationToken ct = default);
}
