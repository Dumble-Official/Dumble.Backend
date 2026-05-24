using Dumble.SharedKernel.Common;
using System.Text.Json.Serialization;

namespace Dumble.SharedKernel.Events.Subscription;

public record RenewalPromptEvent(
    [property: JsonPropertyName("userId")] Guid? UserId,
    [property: JsonPropertyName("subscriptionId")] Guid? SubscriptionId,
    [property: JsonPropertyName("participantId")] Guid? ParticipantId,
    [property: JsonPropertyName("amountCents")] long? AmountCents,
    [property: JsonPropertyName("currency")] string? Currency,
    [property: JsonPropertyName("reason")] string? Reason
) : IntegrationEvent;
