namespace Dumble.RecommendationService.Application.Contracts;

/// <summary>
/// Mints a short-lived service JWT so background jobs can call sibling services directly
/// (bypassing the gateway) and still pass their JWT validation. There is no end-user request
/// in flight during a reconcile, so there is no caller token to forward.
/// </summary>
public interface IServiceTokenProvider
{
    string CreateToken();
}
