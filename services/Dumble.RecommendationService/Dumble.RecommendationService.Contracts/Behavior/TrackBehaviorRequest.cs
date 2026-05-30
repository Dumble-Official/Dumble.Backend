namespace Dumble.RecommendationService.Contracts.Behavior;

/// <summary>
/// A client-reported behaviour signal. Same wire shape the app already sends (relocated
/// from SocialService): <see cref="EventType"/> is one of View / Click / TimeSpent, and for
/// TimeSpent <see cref="EventData"/> carries the dwell duration in whole seconds.
/// </summary>
public sealed record TrackBehaviorRequest(string PostId, string EventType, string? EventData);

/// <summary>A batch of client behaviour signals reported in one request.</summary>
public sealed record BatchTrackBehaviorRequest(List<TrackBehaviorRequest> Events);
