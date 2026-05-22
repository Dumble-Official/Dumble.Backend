using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Subscription;

public record SellerBannedEvent(
    Guid SellerId,
    string? Reason
) : IntegrationEvent;
