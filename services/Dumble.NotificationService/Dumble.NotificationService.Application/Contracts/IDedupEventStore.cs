namespace Dumble.NotificationService.Application.Contracts;

public interface IDedupEventStore
{
    Task<bool> TryClaimAsync(string messageId, string consumerType, CancellationToken ct);
}
