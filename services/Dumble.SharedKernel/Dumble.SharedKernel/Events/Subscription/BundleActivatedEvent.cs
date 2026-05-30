using Dumble.SharedKernel.Common;
using System.Text.Json.Serialization;

namespace Dumble.SharedKernel.Events.Subscription;

public record BundleActivatedEvent(
    [property: JsonPropertyName("id")] Guid Id,
    [property: JsonPropertyName("participantId")] Guid ParticipantId,
    [property: JsonPropertyName("sellerId")] Guid SellerId,
    [property: JsonPropertyName("bundleName")] string BundleName,
    [property: JsonPropertyName("pricePaidCents")] long PricePaidCents,
    [property: JsonPropertyName("currency")] string Currency,
    [property: JsonPropertyName("durationDays")] int DurationDays
) : IntegrationEvent;
