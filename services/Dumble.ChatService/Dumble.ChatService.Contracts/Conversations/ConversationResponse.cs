namespace Dumble.ChatService.Contracts.Conversations;
public record ConversationResponse(
    string Id, string Type, string? Name, string? ImageUrl,
    List<ParticipantResponse> Participants,
    LastMessageResponse? LastMessage,
    DateTime CreatedAt, DateTime UpdatedAt,
    // Messages in this conversation, from other people, that the requesting user
    // hasn't read yet. Populated by the conversations-list query; 0 elsewhere.
    int UnreadCount = 0);

public record ParticipantResponse(
    string UserId, string DisplayName, string? ProfileImage,
    string Role, DateTime JoinedAt, string? LastReadMessageId);

public record LastMessageResponse(
    string MessageId, string SenderId, string SenderName,
    string Content, DateTime SentAt);
