namespace Dumble.SharedKernel.Events.Chat;

public record MessageSentEvent(
    string ConversationId,
    string SenderId,
    string SenderName,
    List<string> RecipientIds,
    string Preview,
    DateTime SentAt
);
