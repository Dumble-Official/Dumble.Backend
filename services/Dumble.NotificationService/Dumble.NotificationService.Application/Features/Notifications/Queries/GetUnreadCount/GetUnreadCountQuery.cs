using MediatR;
using Dumble.NotificationService.Contracts.Notifications;

namespace Dumble.NotificationService.Application.Features.Notifications.Queries.GetUnreadCount;

public record GetUnreadCountQuery(string RecipientId) : IRequest<UnreadCountResponse>;
