using MediatR;

namespace Dumble.NotificationService.Application.Features.Notifications.Commands.MarkAsRead;

public record MarkAsReadCommand(string NotificationId, string CallerId) : IRequest;
