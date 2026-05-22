using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Subscription;

public record RenewalPromptEvent(
    Guid? UserId,
    Guid? SubscriptionId,
    Guid? ParticipantId,
    long? AmountCents,
    string? Currency,
    string? Reason
) : IntegrationEvent;
