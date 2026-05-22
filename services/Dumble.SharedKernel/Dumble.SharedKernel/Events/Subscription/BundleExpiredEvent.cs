using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Subscription;

public record BundleExpiredEvent(
    Guid SubscriptionId,
    string? Reason
) : IntegrationEvent;
