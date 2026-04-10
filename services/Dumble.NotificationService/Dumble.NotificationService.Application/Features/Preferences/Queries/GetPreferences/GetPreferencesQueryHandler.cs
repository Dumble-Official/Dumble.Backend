using MediatR;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Contracts.Preferences;

namespace Dumble.NotificationService.Application.Features.Preferences.Queries.GetPreferences;

public class GetPreferencesQueryHandler : IRequestHandler<GetPreferencesQuery, NotificationPreferenceResponse>
{
    private readonly INotificationPreferenceRepository _repository;

    public GetPreferencesQueryHandler(INotificationPreferenceRepository repository)
    {
        _repository = repository;
    }

    public async Task<NotificationPreferenceResponse> Handle(GetPreferencesQuery request, CancellationToken ct)
    {
        var pref = await _repository.GetByUserIdAsync(request.UserId, ct);

        var preferences = pref?.Preferences.ToDictionary(
            kvp => kvp.Key,
            kvp => new ChannelPreferenceDto(kvp.Value.Push, kvp.Value.InApp)
        ) ?? new Dictionary<string, ChannelPreferenceDto>();

        return new NotificationPreferenceResponse(preferences);
    }
}
