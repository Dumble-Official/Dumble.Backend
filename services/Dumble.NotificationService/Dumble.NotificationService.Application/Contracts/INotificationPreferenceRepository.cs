using Dumble.NotificationService.Domain.Models;

namespace Dumble.NotificationService.Application.Contracts;

public interface INotificationPreferenceRepository
{
    Task<NotificationPreference?> GetByUserIdAsync(string userId, CancellationToken ct = default);
    Task UpsertAsync(NotificationPreference preference, CancellationToken ct = default);
}
