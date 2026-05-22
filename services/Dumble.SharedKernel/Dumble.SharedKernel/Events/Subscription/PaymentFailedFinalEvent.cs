using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Subscription;

public record PaymentFailedFinalEvent(
    Guid? UserId,
    Guid? SubscriptionId,
    int Attempts
) : IntegrationEvent;
