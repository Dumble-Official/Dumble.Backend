using Dumble.SharedKernel.Common;
using System.Text.Json.Serialization;

namespace Dumble.SharedKernel.Events.Subscription;

public record ChargebackProcessedEvent(
    [property: JsonPropertyName("subscriptionId")] Guid SubscriptionId,
    [property: JsonPropertyName("participantId")] Guid ParticipantId,
    [property: JsonPropertyName("lockedCents")] long LockedCents,
    [property: JsonPropertyName("chargebackCents")] long ChargebackCents,
    [property: JsonPropertyName("partial")] bool Partial
) : IntegrationEvent;
