using MediatR;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Contracts.Preferences;
using Dumble.NotificationService.Domain.Models;

namespace Dumble.NotificationService.Application.Features.Preferences.Commands.UpdatePreferences;

public class UpdatePreferencesCommandHandler : IRequestHandler<UpdatePreferencesCommand, NotificationPreferenceResponse>
{
    private readonly INotificationPreferenceRepository _repository;

    public UpdatePreferencesCommandHandler(INotificationPreferenceRepository repository)
    {
        _repository = repository;
    }

    public async Task<NotificationPreferenceResponse> Handle(UpdatePreferencesCommand request, CancellationToken ct)
    {
        var preference = await _repository.GetByUserIdAsync(request.UserId, ct) ?? new NotificationPreference
        {
            UserId = request.UserId
        };

        preference.Preferences = request.Preferences.ToDictionary(
            kvp => kvp.Key,
            kvp => new ChannelPreference { Push = kvp.Value.Push, InApp = kvp.Value.InApp }
        );

        await _repository.UpsertAsync(preference, ct);

        return new NotificationPreferenceResponse(request.Preferences);
    }
}
