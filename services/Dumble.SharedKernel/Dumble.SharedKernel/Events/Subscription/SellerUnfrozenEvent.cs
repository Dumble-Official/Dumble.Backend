using Dumble.SharedKernel.Common;
using System.Text.Json.Serialization;

namespace Dumble.SharedKernel.Events.Subscription;

public record SellerUnfrozenEvent(
    [property: JsonPropertyName("sellerId")] Guid SellerId
) : IntegrationEvent;
