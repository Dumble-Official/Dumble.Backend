using Dumble.SharedKernel.Common;
using System.Text.Json.Serialization;

namespace Dumble.SharedKernel.Events.Subscription;

public record SellerFrozenEvent(
    [property: JsonPropertyName("sellerId")] Guid SellerId,
    [property: JsonPropertyName("frozenUntil")] DateTimeOffset? FrozenUntil,
    [property: JsonPropertyName("reason")] string? Reason
) : IntegrationEvent;
