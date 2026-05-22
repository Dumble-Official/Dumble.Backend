using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Subscription;

public record PaymentFailedEvent(
    Guid? UserId,
    Guid? SubscriptionId,
    int Attempt,
    DateTimeOffset? NextRetryAt
) : IntegrationEvent;
