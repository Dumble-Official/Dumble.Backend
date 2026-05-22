using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Subscription;

public record PlatformActivatedEvent(
    Guid UserId,
    string PlanCode,
    string Status
) : IntegrationEvent;
