using Dumble.NotificationService.Domain.Models;

namespace Dumble.NotificationService.Application.Contracts;

public interface INotificationRepository
{
    Task<Notification?> GetByIdAsync(string id, CancellationToken ct = default);
    Task<List<Notification>> GetByRecipientAsync(string recipientId, DateTime? cursor, int limit, CancellationToken ct = default);
    Task<int> GetUnreadCountAsync(string recipientId, CancellationToken ct = default);
    Task CreateAsync(Notification notification, CancellationToken ct = default);
    Task MarkAsReadAsync(string id, CancellationToken ct = default);
    Task MarkAllAsReadAsync(string recipientId, CancellationToken ct = default);
    Task DeleteAsync(string id, CancellationToken ct = default);

    /// <summary>Delete every notification for a recipient — right-to-be-forgotten.</summary>
    Task DeleteAllForRecipientAsync(string recipientId, CancellationToken ct = default);
}
