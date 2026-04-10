using MediatR;
using Dumble.NotificationService.Contracts.Common;
using Dumble.NotificationService.Contracts.Notifications;

namespace Dumble.NotificationService.Application.Features.Notifications.Queries.GetNotifications;

public record GetNotificationsQuery(string RecipientId, string? Cursor, int Limit = 20)
    : IRequest<CursorPagedResponse<NotificationResponse>>;
