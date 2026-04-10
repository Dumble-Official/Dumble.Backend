using FastEndpoints;
using MediatR;
using Dumble.NotificationService.Application.Features.Preferences.Commands.UpdatePreferences;
using Dumble.NotificationService.Contracts.Preferences;

namespace Dumble.NotificationService.API.Endpoints.Preferences;

public class UpdatePreferencesEndpoint : Endpoint<UpdatePreferencesRequest, NotificationPreferenceResponse>
{
    private readonly IMediator _mediator;

    public UpdatePreferencesEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Put("/api/notifications/preferences");
        Claims("userId");
    }

    public override async Task HandleAsync(UpdatePreferencesRequest req, CancellationToken ct)
    {
        var userId = User.FindFirst("userId")!.Value;
        var result = await _mediator.Send(new UpdatePreferencesCommand(userId, req.Preferences), ct);
        await SendAsync(result, cancellation: ct);
    }
}
