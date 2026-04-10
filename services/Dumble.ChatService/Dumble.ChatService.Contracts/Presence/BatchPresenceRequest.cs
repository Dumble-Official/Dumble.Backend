namespace Dumble.ChatService.Contracts.Presence;
public record BatchPresenceRequest(List<string> UserIds);
public record BatchPresenceResponse(Dictionary<string, bool> Statuses);
