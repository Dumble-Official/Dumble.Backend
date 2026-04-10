namespace Dumble.ChatService.Contracts.Conversations;
public record ConversationResponse(
    string Id, string Type, string? Name, string? ImageUrl,
    List<ParticipantResponse> Participants,
    LastMessageResponse? LastMessage,
    DateTime CreatedAt, DateTime UpdatedAt);

public record ParticipantResponse(
    string UserId, string DisplayName, string? ProfileImage,
    string Role, DateTime JoinedAt);

public record LastMessageResponse(
    string MessageId, string SenderId, string SenderName,
    string Content, DateTime SentAt);
