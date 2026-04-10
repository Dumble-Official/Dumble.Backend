using FirebaseAdmin.Messaging;
using Dumble.NotificationService.Application.Contracts;

namespace Dumble.NotificationService.Infrastructure.Push;

public class FirebasePushNotificationService : IPushNotificationService
{
    public async Task SendAsync(List<string> deviceTokens, string title, string body,
        Dictionary<string, string>? data = null, CancellationToken ct = default)
    {
        if (deviceTokens.Count == 0) return;

        var message = new MulticastMessage
        {
            Tokens = deviceTokens,
            Notification = new FirebaseAdmin.Messaging.Notification
            {
                Title = title,
                Body = body
            },
            Data = data
        };

        try
        {
            await FirebaseMessaging.DefaultInstance.SendEachForMulticastAsync(message, ct);
        }
        catch (Exception)
        {
            // Log but don't throw — push failures shouldn't break the flow
        }
    }
}
