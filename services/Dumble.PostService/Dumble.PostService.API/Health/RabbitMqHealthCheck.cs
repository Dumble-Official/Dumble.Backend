using MassTransit;
using Microsoft.Extensions.Diagnostics.HealthChecks;

namespace Dumble.PostService.API.Health;

/// <summary>
/// Reports degraded when the MassTransit bus isn't started or the broker
/// connection isn't holding. Without this, /health/ready returns 200 even
/// when RabbitMQ is unreachable and event publishes are silently failing —
/// orchestrators won't pull traffic away from a half-broken pod.
/// </summary>
public sealed class RabbitMqHealthCheck : IHealthCheck
{
    private readonly IBusControl _bus;

    public RabbitMqHealthCheck(IBusControl bus)
    {
        _bus = bus;
    }

    public async Task<HealthCheckResult> CheckHealthAsync(
        HealthCheckContext context,
        CancellationToken cancellationToken = default)
    {
        try
        {
            var probe = await _bus.GetProbeResult().ConfigureAwait(false);
            if (string.Equals(probe.Status, "ready", StringComparison.OrdinalIgnoreCase))
            {
                return HealthCheckResult.Healthy("MassTransit bus is ready");
            }
            return HealthCheckResult.Degraded($"MassTransit bus status: {probe.Status}");
        }
        catch (Exception ex)
        {
            return HealthCheckResult.Unhealthy("MassTransit bus probe failed", ex);
        }
    }
}
