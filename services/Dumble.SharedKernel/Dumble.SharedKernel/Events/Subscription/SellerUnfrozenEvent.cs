using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Subscription;

public record SellerUnfrozenEvent(
    Guid SellerId
) : IntegrationEvent;
