using Dumble.SharedKernel.Common;
using System.Text.Json.Serialization;

namespace Dumble.SharedKernel.Events.Subscription;

public record PlatformActivatedEvent(
    [property: JsonPropertyName("userId")] Guid? UserId,
    [property: JsonPropertyName("planCode")] string PlanCode,
    [property: JsonPropertyName("status")] string Status
) : IntegrationEvent;
