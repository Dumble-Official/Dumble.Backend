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

    public async Task<HealthCheckResult> CheckHealthAsync(
        HealthCheckContext context,
        CancellationToken cancellationToken = default)
    {
        try
        {
            _bus.GetProbeResult();
            return HealthCheckResult.Healthy("MassTransit bus is ready");
        }
        catch (Exception ex)
        {
            return HealthCheckResult.Unhealthy("MassTransit bus check failed", ex);
        }
    }
}
