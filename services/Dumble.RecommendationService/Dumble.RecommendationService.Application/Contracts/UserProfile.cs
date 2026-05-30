namespace Dumble.RecommendationService.Application.Contracts;

/// <summary>Minimal user profile kept in the projection for hydrating suggestion cards.</summary>
public sealed record UserProfile(string DisplayName, string? ProfileImage);
