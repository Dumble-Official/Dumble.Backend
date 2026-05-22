using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Subscription;

public record PlatformExpiredEvent(
    Guid UserId,
    string? Reason
) : IntegrationEvent;
