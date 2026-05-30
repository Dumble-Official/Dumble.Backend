using Dumble.SharedKernel.Common;
using System.Text.Json.Serialization;

namespace Dumble.SharedKernel.Events.Subscription;

public record RefundIssuedEvent(
    [property: JsonPropertyName("subscriptionId")] Guid SubscriptionId,
    [property: JsonPropertyName("participantId")] Guid ParticipantId,
    [property: JsonPropertyName("amountCents")] long AmountCents
) : IntegrationEvent;
