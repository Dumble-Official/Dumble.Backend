using MediatR;

namespace Dumble.NotificationService.Application.Features.Notifications.Commands.DeleteNotification;

public record DeleteNotificationCommand(string NotificationId, string CallerId) : IRequest;
