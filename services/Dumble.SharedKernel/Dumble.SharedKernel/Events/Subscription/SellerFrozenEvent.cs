using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Subscription;

public record SellerFrozenEvent(
    Guid SellerId,
    DateTimeOffset? FrozenUntil,
    string? Reason
) : IntegrationEvent;
