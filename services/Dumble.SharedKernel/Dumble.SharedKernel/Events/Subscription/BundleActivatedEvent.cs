using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Subscription;

public record BundleActivatedEvent(
    Guid SubscriptionId,
    Guid ParticipantId,
    Guid SellerId,
    string BundleName,
    long PricePaidCents,
    string Currency,
    int DurationDays
) : IntegrationEvent;
