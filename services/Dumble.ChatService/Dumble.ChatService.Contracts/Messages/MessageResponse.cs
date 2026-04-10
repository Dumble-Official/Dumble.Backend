namespace Dumble.ChatService.Contracts.Messages;
public record MessageResponse(
    string Id, string ConversationId, string SenderId, string SenderName,
    string? SenderProfileImage, string Content, string MessageType,
    string? ImageUrl, string? ReplyToMessageId, bool IsDeleted, DateTime CreatedAt);
