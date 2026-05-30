namespace Dumble.RecommendationService.Application.Authentication;

/// <summary>
/// Settings for the service-to-service token the reconcile job presents to PostService.
/// The secret is the same base64 JWT_SECRET every service validates against.
/// </summary>
public sealed class ServiceAuthOptions
{
    /// <summary>Base64-encoded HMAC key — identical to the JWT_SECRET used for inbound validation.</summary>
    public string Secret { get; set; } = "";

    /// <summary>Stable service identity stamped as sub/userId. A UUID so userId-shaped checks pass.</summary>
    public string ServiceUserId { get; set; } = "00000000-0000-4000-8000-0000000000a1";

    /// <summary>How long a minted token is valid. Kept short — it is created fresh per call.</summary>
    public int TokenLifetimeMinutes { get; set; } = 5;

    public bool IsConfigured => !string.IsNullOrWhiteSpace(Secret);
}
