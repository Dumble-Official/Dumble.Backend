using MediatR;

namespace Dumble.NotificationService.Application.Features.Notifications.Commands.MarkAllAsRead;

public record MarkAllAsReadCommand(string RecipientId) : IRequest;
