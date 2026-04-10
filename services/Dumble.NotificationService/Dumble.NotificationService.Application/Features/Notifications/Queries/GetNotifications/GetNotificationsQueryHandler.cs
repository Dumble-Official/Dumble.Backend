using MediatR;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Contracts.Common;
using Dumble.NotificationService.Contracts.Notifications;

namespace Dumble.NotificationService.Application.Features.Notifications.Queries.GetNotifications;

public class GetNotificationsQueryHandler : IRequestHandler<GetNotificationsQuery, CursorPagedResponse<NotificationResponse>>
{
    private readonly INotificationRepository _repository;

    public GetNotificationsQueryHandler(INotificationRepository repository)
    {
        _repository = repository;
    }

    public async Task<CursorPagedResponse<NotificationResponse>> Handle(GetNotificationsQuery request, CancellationToken ct)
    {
        DateTime? cursor = request.Cursor is not null ? DateTime.Parse(request.Cursor) : null;

        var notifications = await _repository.GetByRecipientAsync(request.RecipientId, cursor, request.Limit + 1, ct);
        var hasMore = notifications.Count > request.Limit;
        if (hasMore) notifications = notifications.Take(request.Limit).ToList();

        var items = notifications.Select(n => new NotificationResponse(
            n.Id, n.Type, n.Title, n.Body, n.Data, n.IsRead, n.CreatedAt
        )).ToList();

        var nextCursor = hasMore && items.Count > 0
            ? items.Last().CreatedAt.ToString("O")
            : null;

        return new CursorPagedResponse<NotificationResponse>(items, nextCursor, hasMore);
    }
}
