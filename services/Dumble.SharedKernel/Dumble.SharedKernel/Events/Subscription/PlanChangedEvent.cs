using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Subscription;

public record PlanChangedEvent(
    Guid UserId,
    string NewPlan
) : IntegrationEvent;
