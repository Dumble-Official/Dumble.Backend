using Dumble.NotificationService.Domain.Models;

namespace Dumble.NotificationService.Application.Contracts;

public interface INotificationPreferenceRepository
{
    Task<NotificationPreference?> GetByUserIdAsync(string userId, CancellationToken ct = default);
    Task UpsertAsync(NotificationPreference preference, CancellationToken ct = default);

    /// <summary>Delete a user's notification preference — right-to-be-forgotten.</summary>
    Task DeleteForUserAsync(string userId, CancellationToken ct = default);
}
