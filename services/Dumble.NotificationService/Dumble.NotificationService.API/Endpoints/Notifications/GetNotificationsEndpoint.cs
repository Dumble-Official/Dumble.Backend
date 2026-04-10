using FastEndpoints;
using MediatR;
using Dumble.NotificationService.Application.Features.Notifications.Queries.GetNotifications;
using Dumble.NotificationService.Contracts.Common;
using Dumble.NotificationService.Contracts.Notifications;

namespace Dumble.NotificationService.API.Endpoints.Notifications;

public class GetNotificationsEndpoint : EndpointWithoutRequest<CursorPagedResponse<NotificationResponse>>
{
    private readonly IMediator _mediator;

    public GetNotificationsEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/notifications");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var userId = User.FindFirst("userId")!.Value;
        var cursor = Query<string?>("cursor");
        var limit = Query<int?>("limit") ?? 20;

        var result = await _mediator.Send(new GetNotificationsQuery(userId, cursor, limit), ct);
        await SendAsync(result, cancellation: ct);
    }
}
