using Dumble.SharedKernel.Common;
using System.Text.Json.Serialization;

namespace Dumble.SharedKernel.Events.Subscription;

public record PaymentFailedFinalEvent(
    [property: JsonPropertyName("userId")] Guid? UserId,
    [property: JsonPropertyName("subscriptionId")] Guid? SubscriptionId,
    [property: JsonPropertyName("attempts")] int Attempts
) : IntegrationEvent;
