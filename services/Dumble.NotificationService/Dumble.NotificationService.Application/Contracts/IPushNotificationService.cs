namespace Dumble.NotificationService.Application.Contracts;

public interface IPushNotificationService
{
    Task SendAsync(List<string> deviceTokens, string title, string body, Dictionary<string, string>? data = null, CancellationToken ct = default);
}
