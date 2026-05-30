using Dumble.SharedKernel.Common;
using System.Text.Json.Serialization;

namespace Dumble.SharedKernel.Events.Subscription;

public record PlatformExpiredEvent(
    [property: JsonPropertyName("userId")] Guid UserId,
    [property: JsonPropertyName("reason")] string? Reason
) : IntegrationEvent;
