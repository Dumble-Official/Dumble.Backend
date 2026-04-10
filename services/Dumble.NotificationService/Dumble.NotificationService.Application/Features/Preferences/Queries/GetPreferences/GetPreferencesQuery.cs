using MediatR;
using Dumble.NotificationService.Contracts.Preferences;

namespace Dumble.NotificationService.Application.Features.Preferences.Queries.GetPreferences;

public record GetPreferencesQuery(string UserId) : IRequest<NotificationPreferenceResponse>;
