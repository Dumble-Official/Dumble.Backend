namespace Dumble.ChatService.Contracts.Messages;
public record SendMessageRequest(string Content, string? ReplyToMessageId, string? ImageUrl = null);
