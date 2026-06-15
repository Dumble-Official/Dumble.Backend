namespace Dumble.NotificationService.Contracts.Devices;

public record DeviceResponse(
    string Id,
    string Token,
    string Platform,
    DateTime CreatedAt
);
