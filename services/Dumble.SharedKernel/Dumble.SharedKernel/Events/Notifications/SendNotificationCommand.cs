namespace Dumble.SharedKernel.Events.Notifications;

public record SendNotificationCommand(
    string RecipientId,
    string Type,
    string Title,
    string Body,
    Dictionary<string, string> Data
);
