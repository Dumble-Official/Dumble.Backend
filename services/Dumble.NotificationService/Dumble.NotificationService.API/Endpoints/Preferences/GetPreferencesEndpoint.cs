using FastEndpoints;
using MediatR;
using Dumble.NotificationService.Application.Features.Preferences.Queries.GetPreferences;
using Dumble.NotificationService.Contracts.Preferences;

namespace Dumble.NotificationService.API.Endpoints.Preferences;

public class GetPreferencesEndpoint : EndpointWithoutRequest<NotificationPreferenceResponse>
{
    private readonly IMediator _mediator;

    public GetPreferencesEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/notifications/preferences");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var userId = User.FindFirst("userId")!.Value;
        var result = await _mediator.Send(new GetPreferencesQuery(userId), ct);
        await SendAsync(result, cancellation: ct);
    }
}
