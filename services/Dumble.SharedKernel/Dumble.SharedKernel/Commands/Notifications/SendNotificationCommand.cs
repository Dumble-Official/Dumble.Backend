using Dumble.SharedKernel.Enums;

namespace Dumble.SharedKernel.Commands.Notifications;

public record SendNotificationCommand(
    string RecipientId,
    NotificationType Type,
    string Title,
    string Body,
    IReadOnlyDictionary<string, string> Data
);
