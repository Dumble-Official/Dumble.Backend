using MassTransit;
using Microsoft.Extensions.Diagnostics.HealthChecks;

namespace Dumble.PostService.API.Health;

public sealed class RabbitMqHealthCheck : IHealthCheck
{
    private readonly IBus _bus;

    public RabbitMqHealthCheck(IBus bus)
    {
        _bus = bus;
    }

    public Task<HealthCheckResult> CheckHealthAsync(
        HealthCheckContext context,
        CancellationToken cancellationToken = default)
    {
        try
        {
            _bus.GetProbeResult();
            return Task.FromResult(HealthCheckResult.Healthy("MassTransit bus is ready"));
        }
        catch (Exception ex)
        {
            return Task.FromResult(HealthCheckResult.Unhealthy("MassTransit bus connection failed", ex));
        }
    }
}
