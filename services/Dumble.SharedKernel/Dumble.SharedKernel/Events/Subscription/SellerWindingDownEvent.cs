using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Subscription;

public record SellerWindingDownEvent(
    Guid SellerId,
    string? Reason
) : IntegrationEvent;
