namespace Dumble.SocialService.Contracts.Behavior;

public record TrackBehaviorRequest(string PostId, string EventType, string? EventData);

public record BatchTrackBehaviorRequest(List<TrackBehaviorRequest> Events);
