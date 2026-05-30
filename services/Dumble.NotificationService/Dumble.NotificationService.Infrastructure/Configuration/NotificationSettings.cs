namespace Dumble.NotificationService.Infrastructure.Configuration;

public class NotificationSettings
{
    public const string SectionName = "Notification";
    public int DefaultExpiryDays { get; set; } = 30;
}
