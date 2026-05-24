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
            var healthResult = _bus.CheckHealth();
            return healthResult.Status == BusHealthStatus.Healthy
                ? Task.FromResult(HealthCheckResult.Healthy("MassTransit bus is ready"))
                : Task.FromResult(HealthCheckResult.Unhealthy(
                    $"MassTransit bus status: {healthResult.Status}"));
        }
        catch (Exception ex)
        {
            return Task.FromResult(HealthCheckResult.Unhealthy("MassTransit bus connection failed", ex));
        }
    }
}
