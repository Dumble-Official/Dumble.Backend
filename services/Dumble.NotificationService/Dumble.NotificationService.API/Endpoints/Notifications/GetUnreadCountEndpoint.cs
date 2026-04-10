using FastEndpoints;
using MediatR;
using Dumble.NotificationService.Application.Features.Notifications.Queries.GetUnreadCount;
using Dumble.NotificationService.Contracts.Notifications;

namespace Dumble.NotificationService.API.Endpoints.Notifications;

public class GetUnreadCountEndpoint : EndpointWithoutRequest<UnreadCountResponse>
{
    private readonly IMediator _mediator;

    public GetUnreadCountEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/notifications/unread/count");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var userId = User.FindFirst("userId")!.Value;
        var result = await _mediator.Send(new GetUnreadCountQuery(userId), ct);
        await SendAsync(result, cancellation: ct);
    }
}
