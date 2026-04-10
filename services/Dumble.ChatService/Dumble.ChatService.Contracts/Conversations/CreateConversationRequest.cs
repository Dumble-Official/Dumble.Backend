namespace Dumble.ChatService.Contracts.Conversations;
public record CreateConversationRequest(string Type, string? Name, List<string> ParticipantIds);
