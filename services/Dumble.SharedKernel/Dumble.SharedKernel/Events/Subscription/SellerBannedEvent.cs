using Dumble.SharedKernel.Common;
using System.Text.Json.Serialization;

namespace Dumble.SharedKernel.Events.Subscription;

public record SellerBannedEvent(
    [property: JsonPropertyName("sellerId")] Guid SellerId,
    [property: JsonPropertyName("reason")] string? Reason
) : IntegrationEvent;
