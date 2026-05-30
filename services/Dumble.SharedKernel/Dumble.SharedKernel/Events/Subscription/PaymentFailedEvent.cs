using Dumble.SharedKernel.Common;
using System.Text.Json.Serialization;

namespace Dumble.SharedKernel.Events.Subscription;

public record PaymentFailedEvent(
    [property: JsonPropertyName("userId")] Guid? UserId,
    [property: JsonPropertyName("subscriptionId")] Guid? SubscriptionId,
    [property: JsonPropertyName("attempt")] int Attempt,
    [property: JsonPropertyName("nextRetryAt")] DateTimeOffset? NextRetryAt
) : IntegrationEvent;
