using MediatR;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Contracts.Notifications;

namespace Dumble.NotificationService.Application.Features.Notifications.Queries.GetUnreadCount;

public class GetUnreadCountQueryHandler : IRequestHandler<GetUnreadCountQuery, UnreadCountResponse>
{
    private readonly INotificationRepository _repository;

    public GetUnreadCountQueryHandler(INotificationRepository repository)
    {
        _repository = repository;
    }

    public async Task<UnreadCountResponse> Handle(GetUnreadCountQuery request, CancellationToken ct)
    {
        var count = await _repository.GetUnreadCountAsync(request.RecipientId, ct);
        return new UnreadCountResponse(count);
    }
}
