using MassTransit;
using Microsoft.Extensions.Diagnostics.HealthChecks;

namespace Dumble.PostService.API.Health;

public sealed class RabbitMqHealthCheck : IHealthCheck
{
    private readonly IBusControl _bus;

    public RabbitMqHealthCheck(IBusControl bus)
    {
        _bus = bus;
    }

    public Task<HealthCheckResult> CheckHealthAsync(
        HealthCheckContext context,
        CancellationToken cancellationToken = default)
    {
        try
        {
            var probe = _bus.GetProbeResult();
            var isReady = string.Equals(probe.Status, "ready", StringComparison.OrdinalIgnoreCase);
            return Task.FromResult(isReady
                ? HealthCheckResult.Healthy("MassTransit bus is ready")
                : HealthCheckResult.Degraded($"MassTransit bus status: {probe.Status}"));
        }
        catch (Exception ex)
        {
            return Task.FromResult(HealthCheckResult.Unhealthy("MassTransit bus check failed", ex));
        }
    }
}
