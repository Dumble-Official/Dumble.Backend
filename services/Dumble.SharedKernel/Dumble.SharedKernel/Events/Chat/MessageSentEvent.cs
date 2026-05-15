using Dumble.SharedKernel.Common;

namespace Dumble.SharedKernel.Events.Chat;

public record MessageSentEvent(
    string ConversationId,
    string SenderId,
    string SenderName,
    string? SenderImage,
    IReadOnlyList<string> RecipientIds,
    string Preview,
    DateTimeOffset SentAt
) : IntegrationEvent;
