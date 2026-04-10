namespace Dumble.NotificationService.Contracts.Notifications;

public record NotificationResponse(
    string Id,
    string Type,
    string Title,
    string Body,
    Dictionary<string, string> Data,
    bool IsRead,
    DateTime CreatedAt
);
