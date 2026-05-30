using Dumble.SharedKernel.Common;
using System.Text.Json.Serialization;

namespace Dumble.SharedKernel.Events.Subscription;

public record PlanChangedEvent(
    [property: JsonPropertyName("userId")] Guid? UserId,
    [property: JsonPropertyName("newPlan")] string? NewPlan
) : IntegrationEvent;
