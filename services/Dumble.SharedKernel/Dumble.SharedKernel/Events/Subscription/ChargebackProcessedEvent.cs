using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Subscription;

public record ChargebackProcessedEvent(
    Guid ChargebackId,
    Guid SubscriptionId,
    long AmountCents
) : IntegrationEvent;
