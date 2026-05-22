using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Subscription;

public record ReceiptIssuedEvent(
    Guid ReceiptId,
    Guid UserId,
    long AmountCents,
    string Currency
) : IntegrationEvent;
