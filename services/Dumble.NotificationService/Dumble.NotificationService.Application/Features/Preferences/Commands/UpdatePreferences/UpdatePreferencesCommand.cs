using MediatR;
using Dumble.NotificationService.Contracts.Preferences;

namespace Dumble.NotificationService.Application.Features.Preferences.Commands.UpdatePreferences;

public record UpdatePreferencesCommand(string UserId, Dictionary<string, ChannelPreferenceDto> Preferences)
    : IRequest<NotificationPreferenceResponse>;
