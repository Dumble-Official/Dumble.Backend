namespace Dumble.NotificationService.Contracts.Preferences;

public record UpdatePreferencesRequest(
    Dictionary<string, ChannelPreferenceDto> Preferences
);
