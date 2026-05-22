using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Subscription;

public record SellerClosedEvent(
    Guid SellerId,
    string? Reason
) : IntegrationEvent;
