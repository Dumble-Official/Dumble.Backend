namespace Dumble.NotificationService.Contracts.Preferences;

public record NotificationPreferenceResponse(
    Dictionary<string, ChannelPreferenceDto> Preferences
);

public record ChannelPreferenceDto(bool Push, bool InApp);
