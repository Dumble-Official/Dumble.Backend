using Dumble.SharedKernel.Common;
using System.Text.Json.Serialization;

namespace Dumble.SharedKernel.Events.Subscription;

public record BundleExpiredEvent(
    [property: JsonPropertyName("subscriptionId")] Guid SubscriptionId,
    [property: JsonPropertyName("reason")] string? Reason
) : IntegrationEvent;
