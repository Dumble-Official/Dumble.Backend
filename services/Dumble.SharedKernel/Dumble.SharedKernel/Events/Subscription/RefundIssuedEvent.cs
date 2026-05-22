using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Subscription;

public record RefundIssuedEvent(
    Guid RefundId,
    Guid SubscriptionId,
    long AmountCents,
    string? Reason
) : IntegrationEvent;
