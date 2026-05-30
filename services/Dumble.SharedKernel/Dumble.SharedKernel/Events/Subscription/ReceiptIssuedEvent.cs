using Dumble.SharedKernel.Common;
using System.Text.Json.Serialization;

namespace Dumble.SharedKernel.Events.Subscription;

public record ReceiptIssuedEvent(
    [property: JsonPropertyName("receiptId")] Guid ReceiptId,
    [property: JsonPropertyName("userId")] Guid UserId,
    [property: JsonPropertyName("amountCents")] long AmountCents,
    [property: JsonPropertyName("currency")] string Currency
) : IntegrationEvent;
